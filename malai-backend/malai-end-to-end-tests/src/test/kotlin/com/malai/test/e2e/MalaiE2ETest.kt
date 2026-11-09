package com.malai.test.e2e

import tools.jackson.module.kotlin.readValue
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.*
import com.malai.test.acceptance.kensa.MalaiKensaExtension
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import com.malai.test.acceptance.kensa.fixtures.deleteAllFixtures
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.fixtures.FixtureCapture
import com.neovibrant.kacey.matchers.ApiJsonMatcher
import io.kotest.matchers.should
import com.neovibrant.kacey.matchers.Prop
import dev.kensa.StateCollector
import dev.kensa.junit.KensaTest
import dev.kensa.kotest.WithKotest
import dev.kensa.state.Party
import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.ResponseEntity
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared base for every malai end-to-end test. Mirrors [com.malai.test.acceptance.MalaiAcceptanceTest]
 * but targets remote services running in Docker containers: the three per-service [HttpClientStub]s
 * all share a single last-response [AtomicReference], so tests can ask `theLastResponseHas()` after
 * any `*ServiceReceives(...)` call.
 *
 * Unlike acceptance tests, there is no in-JVM Spring app here — so this class does NOT mix in
 * `WithApplicationContext`, `WithStubbedTime` or `WithTestUuidGenerator`. Test state is primed
 * through the services' HTTP APIs via the plain `preconditions*` RestClients on
 * [MalaiE2ETestContext], wrapped in the domain builders in the `fixtures` package.
 */
@ExtendWith(MalaiKensaExtension::class)
abstract class MalaiE2ETest : KensaTest, WithKotest, WithWiremock,
                              WithIdentityProvider, WithCognitoJwtToken,
                              WithAuthService, WithAccountHolderService, WithAccountService,
                              WithPayeeService, WithPaymentsService, WithSandboxService,
                              WithStandingOrderService, WithDirectDebitService,
                              WithCardService, WithMalaiStub, WithMdrService, WithCopilotService,
                              WithAmlService,
                              MalaiTestFixtures, WithAuditEvents, WithFunding, WithPaymentsDb,
                              WithCardsDb, WithInsights {

    private val sharedLastResponse: AtomicReference<ResponseEntity<String>> = AtomicReference()

    override val authServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.authServiceHttpClient, sharedLastResponse)

    override val accountHolderServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.accountHolderServiceHttpClient, sharedLastResponse)

    override val accountServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.accountServiceHttpClient, sharedLastResponse)

    override val payeeServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.payeeServiceHttpClient, sharedLastResponse)

    override val paymentsServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.paymentsServiceHttpClient, sharedLastResponse)

    override val standingOrderServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.standingOrderServiceHttpClient, sharedLastResponse)

    override val directDebitServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.directDebitServiceHttpClient, sharedLastResponse)

    override val sandboxServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.sandboxServiceHttpClient, sharedLastResponse)

    override val cardServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.cardServiceHttpClient, sharedLastResponse)

    override val mdrServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.mdrServiceHttpClient, sharedLastResponse)

    override val copilotServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.copilotServiceHttpClient, sharedLastResponse)

    override val amlServiceHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.amlServiceHttpClient, sharedLastResponse)

    override val malaiStubHttpClient: HttpClientStub =
        HttpClientStub(MalaiE2ETestContext.malaiStubHttpClient, sharedLastResponse)

    /**
     * The services run in Docker containers and validate tokens against
     * `http://host.docker.internal:9090/stubbed-cognito-issuer` — the URL reachable *from the
     * containers*. Override the default `wiremock().url(...)` (which returns `http://localhost:9090/...`
     * from the JVM's viewpoint) so the signed JWTs carry an issuer claim that matches what the
     * services expect.
     */
    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL

    private var usedMalaiE2eTestWrapper = false

    /**
     * Every test body must be wrapped in a `malaiE2eTest { ... }` block. Mirrors
     * [com.malai.test.acceptance.MalaiAcceptanceTest.malaiAcceptanceTest]: if a test fails due to
     * something that happens in an `@AfterEach`, Kensa won't detect the failure, so we check for
     * unmatched wiremock requests/stubs from inside the wrapper rather than from an `@AfterEach`
     * hook.
     */
    fun malaiTest(
        checkForUnmatchedStubs: Boolean = true,
        block: () -> Unit,
    ) {
        usedMalaiE2eTestWrapper = true
        block()
        checkForUnmatchedRequests()
        if (checkForUnmatchedStubs) {
            checkForUnmatchedStubs()
        }
        drainKensaEvents()
    }

    private fun drainKensaEvents() {
        val testId = TestIdContext.value.value
        MalaiE2ETestContext.publishTestEnded(testId)
        MalaiE2ETestContext.kensaEventConsumer.awaitTestEnded(testId, java.time.Duration.ofSeconds(10))
    }

    private fun evictServiceTokenCaches() {
        MalaiE2ETestContext.preconditionsAuthServiceHttpClient
            .post()
            .uri("/internal/admin/evict-service-token-caches")
            .retrieve()
            .toBodilessEntity()
    }

    @BeforeEach
    fun primeJwksStubAndCleanState() {
        // WiremockExtension does a resetAll() in beforeEach, so we need to re-prime the stubbed
        // Cognito JWKS endpoint every test. Inter-service HTTP goes container-to-container
        // directly on the compose network now (phase 2c) — WireMock no longer reverse-proxies it.
        primeJwksStub()

        // Drop every ServiceTokenClient's cached Cognito service token so each test's diagram
        // shows the actual `/cognito-service/oauth2/token` fetch alongside the business calls,
        // rather than silently reusing a token cached by an earlier test in the same JVM run.
        evictServiceTokenCaches()

        FixtureCapture.reset()
    }

    @AfterEach
    fun checkForWrapperUse() {
        if (!usedMalaiE2eTestWrapper) {
            fail("Test body should be wrapped in a call to 'malaiE2eTest { ... }', to allow checking of unmatched wiremock requests/stubs")
        }
    }

    /**
     * Reads the last response recorded by any of the three per-service [HttpClientStub]s (they all
     * share a single [AtomicReference], so this returns the response from whichever
     * `*ServiceReceives(...)` was called most recently in this test).
     */
    fun theLastResponseHas() = authServiceHttpClient.receivedResponse()

    open fun lastResponse(): ResponseEntity<String> = authServiceHttpClient.lastResponse()

    /**
     * Parses a UUID-valued [field] out of the most-recently-received JSON response body. Use after
     * a call that returns a server-generated UUID (e.g. `accountUid` from create-account,
     * `accountHolderUid` from create-account-holder) to capture it for use in subsequent steps.
     */
    fun uuidFromLastResponse(field: String): UUID {
        val body = lastResponse().body ?: throw AssertionError("Response body is null")
        val json = testingObjectMapper.readValue<Map<String, Any>>(body)
        return UUID.fromString(json[field] as String)
    }

    /**
     * Parses a UUID at `<listField>[<index>].<itemField>` out of the most-recently-received JSON
     * response body. For listing endpoints (e.g. `GET /api/v1/account-holder/all` returning
     * `{ accountHolders: [{ accountHolderUid: ... }] }`) where the id is generated server-side
     * and the test needs to thread it through follow-up calls.
     */
    fun uuidFromLastResponseList(listField: String, index: Int, itemField: String): UUID {
        val body = lastResponse().body ?: throw AssertionError("Response body is null")
        val json = testingObjectMapper.readValue<Map<String, Any>>(body)
        @Suppress("UNCHECKED_CAST")
        val list = json[listField] as List<Map<String, String>>
        return UUID.fromString(list[index][itemField])
    }

    /**
     * Parses a string-valued [field] out of the most-recently-received JSON response body. Use
     * after a login or refresh-token call to get the Cognito tokens (`accessToken`,
     * `refreshToken`, `idToken`) which are now passed-through directly from Cognito.
     */
    fun stringFromLastResponse(field: String): String {
        val body = lastResponse().body ?: throw AssertionError("Response body is null")
        val json = testingObjectMapper.readValue<Map<String, Any>>(body)
        return json[field] as String
    }

    fun refreshTokenFromLastResponse(): String = stringFromLastResponse("refreshToken")

    /**
     * Reads the value of the `Set-Cookie` header for [name] off the most-recently-received
     * response. Throws if the cookie is absent or has been cleared (Max-Age=0). The e2e
     * `RestClient`s have cookie management disabled (see [MalaiE2ETestContext]) so tests
     * thread cookies explicitly between requests rather than relying on the HTTP client
     * to do it for them — this helper keeps that ergonomic.
     */
    fun cookieValueFromLastResponse(name: String): String {
        val setCookie = lastResponse().headers["Set-Cookie"]
            ?.firstOrNull { it.startsWith("$name=") }
            ?: throw AssertionError("No `Set-Cookie: $name=…` on the last response")
        val maxAge = setCookie.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("Max-Age=") }?.substringAfter("=")
        if (maxAge == "0") {
            throw AssertionError("Cookie '$name' was cleared (Max-Age=0) — the response was a signout, not a session set")
        }
        return setCookie.substringAfter("=").substringBefore(";")
    }

    /**
     * Asserts that the most-recently-received response cleared cookie [name] (Max-Age=0,
     * empty value, same path). Use to verify signout flows.
     */
    fun assertCookieClearedInLastResponse(name: String) {
        val setCookie = lastResponse().headers["Set-Cookie"]
            ?.firstOrNull { it.startsWith("$name=") }
            ?: throw AssertionError("No `Set-Cookie: $name=…` on the last response — signout should have cleared it")
        val attrs = setCookie.split(";").map { it.trim() }
        val value = attrs.first().substringAfter("=")
        val maxAge = attrs.firstOrNull { it.startsWith("Max-Age=") }?.substringAfter("=")
        if (value.isNotEmpty() || maxAge != "0") {
            throw AssertionError("Cookie '$name' was not cleared — Set-Cookie header was: $setCookie")
        }
    }

    /**
     * Returns a [StateCollector] that yields the first HTTP request sent during the current test
     * from [from] to [to] that satisfies [matching]. Routed by destination party: Cognito traffic
     * is still observed by WireMock-on-host (it stubs Cognito), while inter-service traffic is
     * sourced from [KensaEventConsumer]'s envelope index (populated by `AbstractMalaiClient` in
     * the producer containers, so this survives the phase-2c URL switch when WireMock stops
     * seeing inter-service calls entirely). Intended for use inside a Kensa `then(...)` /
     * `and(...)` block:
     *
     * ```
     * and(theFollowingHttpRequestWasMade(MalaiParty.AuthService, MalaiParty.IdentityProvider) {
     *     headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.SignUp"
     * }) {
     *     method shouldBe POST
     *     url shouldEndWith "/cognito/"
     * }
     * ```
     *
     * Order-independent — the predicate picks the request to assert on; other captured requests
     * are ignored. Only requests bearing the current test's `X-TestId` header are considered.
     * If no matching request was captured, the collector throws with the list of requests that
     * were seen, to help diagnose mismatches quickly.
     */
    fun theFollowingHttpRequestWasMade(
        from: Party,
        to: Party,
        matching: CapturedHttpRequest.() -> Boolean,
    ): StateCollector<CapturedHttpRequest> =
        StateCollector { _ ->
            val currentTestId = TestIdContext.value.value
            val capturedRequests = collectCapturedHttpRequests(currentTestId, from, to)
            capturedRequests.firstOrNull(matching)
                ?: error(
                    "Expected an HTTP request from ${from.asString()} to ${to.asString()} " +
                        "matching the given predicate, but none of the ${capturedRequests.size} " +
                        "captured request(s) from ${from.asString()} to ${to.asString()} matched:\n" +
                        capturedRequests.joinToString("\n") { "  - ${it.method} ${it.url}" }
                )
        }

    /**
     * Plural counterpart to [theFollowingHttpRequestWasMade]. Returns ALL captured requests
     * from [from] to [to] matching [matching] in send order. Empty result is allowed -- this
     * is the helper to use when the test asserts on the COUNT of downstream calls (e.g.,
     * proving an idempotent endpoint produced exactly one Cognito call across two HTTP
     * requests with the same Idempotency-Key).
     */
    fun theFollowingHttpRequestsWereMade(
        from: Party,
        to: Party,
        matching: CapturedHttpRequest.() -> Boolean,
    ): StateCollector<List<CapturedHttpRequest>> =
        StateCollector { _ ->
            val currentTestId = TestIdContext.value.value
            collectCapturedHttpRequests(currentTestId, from, to).filter(matching)
        }

    private fun collectCapturedHttpRequests(
        testId: String,
        from: Party,
        to: Party,
    ): List<CapturedHttpRequest> =
        if (from == MalaiParty.IdentityProvider || to == MalaiParty.IdentityProvider ||
            from == MalaiParty.OAuth2Service || to == MalaiParty.OAuth2Service) {
            wiremockCapturedHttpRequests(testId, from, to)
        } else {
            MalaiE2ETestContext.kensaEventConsumer.capturedHttpRequestsFor(testId, from, to)
        }

    private fun wiremockCapturedHttpRequests(
        testId: String,
        from: Party,
        to: Party,
    ): List<CapturedHttpRequest> =
        wiremock().allServeEvents
            .filter { it.request.headers.getHeader(TestId.HEADER).containsValue(testId) }
            .filter { MalaiE2ETestContext.fromSystemLookup(it) == from }
            .filter { MalaiE2ETestContext.toSystemLookup(it.request.url) == to }
            .sortedBy { it.request.loggedDate }
            .map { it.request.toCapturedHttpRequest() }

    fun ResponseEntity<*>.header(name: String): String? =
        this.headers[name]?.firstOrNull()

    infix fun String?.containsProp(json: Prop) {
        if (this == null) {
            fail("The JSON string was null")
        }
        val responsePayload = testingObjectMapper.readValue<Prop>(this)
        responsePayload should ApiJsonMatcher.containsProp(json)
    }

    companion object {
        init {
            MalaiE2ETestContext.boot()
        }

        @JvmStatic
        @BeforeAll
        fun cleanDatabase() {
            cleanTables()
        }

        fun cleanTables() {
            DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL)
                .use { it.deleteAllFixtures() }
        }
    }
}
