package com.malai.test.acceptance

import tools.jackson.module.kotlin.readValue
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.kensa.MalaiKensaExtension
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import com.malai.test.acceptance.kensa.fixtures.SharedDatabaseHandBack
import com.malai.test.acceptance.kensa.fixtures.deleteAllFixtures
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher
import io.kotest.matchers.should
import com.neovibrant.kacey.matchers.Prop
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import dev.kensa.junit.KensaTest
import dev.kensa.kotest.WithKotest
import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.ResponseEntity

/**
 * Shared base for every malai-service acceptance test. Encapsulates the common scaffolding
 * (HTTP client stub, WireMock / JWKS / JWT / time fixtures, unmatched-request checks, JSON
 * matchers, DB cleanup) so each per-service subclass only has to bootstrap its own
 * [MalaiAcceptanceTestContext] (see the subclass's `companion object { init { } }`) and add any
 * service-specific request helpers on top.
 */
@ExtendWith(MalaiKensaExtension::class)
abstract class MalaiAcceptanceTest : KensaTest, WithKotest, WithWiremock, WithApplicationContext,
    WithStubbedTime, WithIdentityProvider, WithCognitoJwtToken, WithServiceJwtToken,
    MalaiTestFixtures {

    val httpClient: HttpClientStub = HttpClientStub(MalaiAcceptanceTestContext.httpClient)

    private var usedMalaiAcceptanceTestWrapper = false

    /**
     * Every test body must be wrapped in a `malaiAcceptanceTest { ... }` block. If a test fails due
     * to something that happens in an `@AfterEach`, Kensa won't detect the failure, so we check
     * for unmatched wiremock requests/stubs from inside the wrapper rather than from an
     * `@AfterEach` hook. The [checkForWrapperUse] `@AfterEach` guards against tests that forget
     * the wrapper.
     */
    fun malaiAcceptanceTest(
        checkForUnmatchedStubs: Boolean = true,
        block: () -> Unit,
    ) {
        usedMalaiAcceptanceTestWrapper = true
        block()
        checkForUnmatchedRequests()
        if (checkForUnmatchedStubs) {
            checkForUnmatchedStubs()
        }
    }

    @BeforeEach
    fun primeJwksStubAndCleanState() {
        // WiremockExtension does a resetAll() in beforeEach, so we need to re-prime the stubbed
        // Cognito JWKS endpoint every test. The same goes for stubbed time.
        primeJwksStub()
        stubbedTimeService().reset()
    }

    @AfterEach
    fun checkForWrapperUse() {
        if (!usedMalaiAcceptanceTestWrapper) {
            fail("Test body should be wrapped in a call to 'malaiAcceptanceTest { ... }', to allow checking of unmatched wiremock requests/stubs")
        }
    }

    /**
     * Send an HTTP request to the running service under test and capture the response.
     * Subclasses typically alias this as e.g. `authServiceReceives(...)` to keep tests readable.
     */
    fun serviceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        httpClient.sends(httpRequest.build())

    fun theLastResponseHas() = httpClient.receivedResponse()

    /**
     * Returns a [StateCollector] that yields the first downstream HTTP request the service under
     * test sent to a WireMock-stubbed dependency during the current test that satisfies
     * [matching]. Intended for use inside a Kensa `then(...)` block:
     *
     * ```
     * then(malaiServiceMadeTheFollowingHttpRequest {
     *     headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
     * }) {
     *     method shouldBe POST
     *     url shouldEndWith "/cognito/"
     *     body containsProp json { "AuthParameters" To json { "USERNAME" To someEmail() } }
     * }
     * ```
     *
     * Order-independent — the predicate picks the request to assert on; other captured
     * requests are ignored, so tests only cover the interactions they care about. Each
     * service's base class typically exposes a sugar alias, e.g.
     * `fun authServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
     *     malaiServiceMadeTheFollowingHttpRequest(matching)`.
     *
     * Only requests bearing the current test's `X-TestId` header are considered, so tests in
     * different classes (or parallel runs sharing a WireMock) don't see each other's traffic.
     * If no matching request was captured, the collector throws with the list of requests that
     * were captured, to help diagnose mismatches quickly.
     */
    fun malaiServiceMadeTheFollowingHttpRequest(
        matching: CapturedHttpRequest.() -> Boolean,
    ): StateCollector<CapturedHttpRequest> =
        StateCollector { _ ->
            val currentTestId = TestIdContext.value.value
            val capturedRequests = wiremock().allServeEvents
                .filter { it.request.headers.getHeader(TestId.HEADER).containsValue(currentTestId) }
                .sortedBy { it.request.loggedDate }
                .map { it.request.toCapturedHttpRequest() }

            capturedRequests.firstOrNull(matching)
                ?: error(
                    "Expected the service under test to have made a downstream HTTP request " +
                            "matching the given predicate, but none of the ${capturedRequests.size} " +
                            "captured request(s) matched:\n" +
                            capturedRequests.joinToString("\n") { "  - ${it.method} ${it.url}" }
                )
        }

    /**
     * Plural counterpart to [malaiServiceMadeTheFollowingHttpRequest]. Yields ALL captured
     * requests matching [matching] in the order they were sent. Use when a test needs to
     * assert on the *count* of downstream calls — e.g., a BACK-07 idempotency replay
     * test that asserts a downstream received exactly one write across two HTTP calls
     * with the same Idempotency-Key.
     *
     * Empty result is allowed (zero matching requests is itself a valid assertion target);
     * unlike the singular variant this collector does not throw when nothing matches.
     */
    fun malaiServiceMadeTheFollowingHttpRequests(
        matching: CapturedHttpRequest.() -> Boolean,
    ): StateCollector<List<CapturedHttpRequest>> =
        StateCollector { _ ->
            val currentTestId = TestIdContext.value.value
            wiremock().allServeEvents
                .filter { it.request.headers.getHeader(TestId.HEADER).containsValue(currentTestId) }
                .sortedBy { it.request.loggedDate }
                .map { it.request.toCapturedHttpRequest() }
                .filter(matching)
        }

    fun ResponseEntity<*>.header(name: String): String? =
        this.headers[name]?.firstOrNull()

    infix fun String?.containsProp(json: Prop) {
        if (this == null) {
            fail("The JSON string was null")
        }

        val responsePayload = testingObjectMapper.readValue<Prop>(this)

        responsePayload should ApiJsonMatcher.containsProp(json)
    }

    infix fun String?.containsPropList(jsons: List<Prop>) {
        if (this == null) {
            fail("The JSON string was null")
        }

        val responsePayload = testingObjectMapper.readValue<List<Prop>>(this)

        responsePayload should ApiJsonMatcher.contains(*jsons.toTypedArray())
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun cleanDatabase() {
            cleanTables()
        }

        fun cleanTables() {
            SharedDatabaseHandBack.recordBorrowing()
            DatabaseConnection.createConnection(MalaiAcceptanceTestContext.DATABASE_URL)
                .use { it.deleteAllFixtures() }
        }
    }
}
