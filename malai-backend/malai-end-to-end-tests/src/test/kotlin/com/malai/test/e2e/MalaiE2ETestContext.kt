package com.malai.test.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.malai.test.acceptance.WireMockHolder
import com.malai.test.acceptance.WithIdentityProvider.Companion.CONTEXT_PATH as IDENTITY_PROVIDER_CONTEXT_PATH
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountHolderService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AmlService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AuthService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.Bacs
import com.malai.test.acceptance.kensa.MalaiParty.Companion.BacsGateway
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CardGateway
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CardScheme
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CardService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CoP
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CopilotService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CounterpartyDirectory
import com.malai.test.acceptance.kensa.MalaiParty.Companion.DirectDebitService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.Fps
import com.malai.test.acceptance.kensa.MalaiParty.Companion.IdentityProvider
import com.malai.test.acceptance.kensa.MalaiParty.Companion.OAuth2Service
import com.malai.test.acceptance.kensa.MalaiParty.Companion.FpsGateway
import com.malai.test.acceptance.kensa.MalaiParty.Companion.InsightsService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.LedgerService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.MalaiStub
import com.malai.test.acceptance.kensa.MalaiParty.Companion.MdrService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.PayeeService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.PaymentsService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.SandboxService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.StandingOrderService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.Swift
import com.malai.test.acceptance.kensa.MalaiParty.Companion.SwiftGateway
import com.malai.commons.tracking.KensaEventEnvelope
import com.malai.commons.tracking.KensaEventKind
import com.malai.test.acceptance.kensa.interaction.FromSystemLookup
import com.malai.test.e2e.kensa.KensaEnvelopePublishingHttpInterceptor
import com.malai.test.acceptance.kensa.interaction.PartyLookupByUrl
import com.malai.test.acceptance.kensa.interaction.WireMockInteractionCapturer
import com.malai.test.acceptance.kensa.misc.WireMockRequestReferenceHandler
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.kensa.KensaEventConsumer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.net.URI
import com.malai.test.e2e.MalaiE2ETestContext.WIREMOCK_PORT
import com.malai.test.e2e.MalaiE2ETestContext.boot
import dev.kensa.Kensa
import dev.kensa.state.Party
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient

/**
 * Singleton holder of the shared infrastructure for a malai end-to-end-test run:
 *
 * - the [WireMockServer] that stubs AWS Cognito for malai-api AND reverse-proxies
 *   inter-service traffic (any `/<service-name>/...` URL is forwarded to the bundled
 *   `malai-api` container with the prefix stripped) so every downstream call passes through
 *   WireMock and lands on the Kensa sequence diagram
 * - per-service [RestClient]s (all pointing at malai-api), each wrapped with a
 *   [MalaiHttpInteractionCapturer] so top-level Client → Service interactions render too
 * - plain `preconditions*` [RestClient]s used to prime state via the services' own HTTP APIs
 *   during the `given` phase, without polluting the Kensa sequence diagram
 *
 * Unlike [com.malai.test.acceptance.MalaiAcceptanceTestContext], malai-api itself runs in a
 * Docker container and is NOT booted in this JVM. WireMock is started on a fixed port
 * ([WIREMOCK_PORT]) — only Cognito-stubbed traffic still flows through it (the OIDC issuer +
 * JWKS endpoint, the SDK bare-path SignUp/InitiateAuth calls, and the service-token endpoint).
 * Inter-service HTTP goes container-to-container on the compose network; production code emits
 * Kensa envelopes via [com.malai.commons.tracking.KensaEventPublisher] and the test JVM's
 * [KensaEventConsumer] drains them onto the sequence diagram.
 */
object MalaiE2ETestContext {

    /**
     * Fixed port used by the in-JVM WireMock. Services running in Docker are preconfigured
     * (via docker-compose) to reach WireMock at `host.docker.internal:<WIREMOCK_PORT>` for the
     * Cognito stubs (OIDC issuer, JWKS, SDK bare-path calls, service-token endpoint), so this
     * port must match what the compose file sets.
     */
    const val WIREMOCK_PORT: Int = 9090

    /**
     * Issuer URL expected by the dockerised services in JWT `iss` claims. Must match
     * `MAL_AWS_COGNITO_JWT_TOKEN_ISSUER` in the docker-compose file — services running in
     * containers reach WireMock at `host.docker.internal`, not `localhost`.
     */
    const val COGNITO_ISSUER_URL: String = "http://host.docker.internal:$WIREMOCK_PORT$IDENTITY_PROVIDER_CONTEXT_PATH"

    private const val E2E_HOST: String = "localhost"

    const val DATABASE_URL: String = "jdbc:postgresql://$E2E_HOST:5433/malai"
    const val DATABASE_USERNAME: String = "malai"
    const val DATABASE_PASSWORD: String = "malai"

    /**
     * Every malai service (auth, account-holder, account, ledger, payee, payments,
     * fps/swift/bacs-gateway) is bundled into the single `malai-api` JVM in production and in
     * e2e — they all live behind the same host:port. The per-service URL constants below are
     * kept as named aliases because each is a logically distinct party on the Kensa sequence
     * diagram even though they share one physical target. The test JVM's per-service RestClients
     * (built via `restClientFor`) tag each top-level call with the right `serviceParty`.
     */
    val MALAI_API_URL: String = "http://$E2E_HOST:8080"

    val AUTH_SERVICE_URL: String = MALAI_API_URL
    val ACCOUNT_HOLDER_SERVICE_URL: String = MALAI_API_URL
    val ACCOUNT_SERVICE_URL: String = MALAI_API_URL
    val PAYEE_SERVICE_URL: String = MALAI_API_URL
    val PAYMENTS_SERVICE_URL: String = MALAI_API_URL
    val STANDING_ORDER_SERVICE_URL: String = MALAI_API_URL
    val DIRECT_DEBIT_SERVICE_URL: String = MALAI_API_URL
    val SANDBOX_SERVICE_URL: String = MALAI_API_URL
    val CARD_SERVICE_URL: String = MALAI_API_URL
    val MDR_SERVICE_URL: String = MALAI_API_URL
    val COPILOT_SERVICE_URL: String = MALAI_API_URL
    val AML_SERVICE_URL: String = MALAI_API_URL
    val LEDGER_SERVICE_URL: String = MALAI_API_URL
    val FPS_GATEWAY_URL: String = MALAI_API_URL
    val SWIFT_GATEWAY_URL: String = MALAI_API_URL
    val BACS_GATEWAY_URL: String = MALAI_API_URL
    val MALAI_STUB_URL: String = "http://$E2E_HOST:9091"
    val LOCALSTACK_URL: String = "http://$E2E_HOST:4567"

    /**
     * Path on the in-JVM WireMock that stubs the OAuth2 service-token `/oauth2/token` endpoint.
     * Each service container's `MAL_AWS_COGNITO_MALAI_SERVICE_TOKEN_ENDPOINT_URL` points here so
     * the register orchestrator can mint service JWTs.
     */
    const val OAUTH2_SERVICE_PROXY_PATH: String = "/oauth2-service"

    val wireMockServer: WireMockServer get() = WireMockHolder.server

    lateinit var authServiceHttpClient: RestClient
        private set

    lateinit var accountHolderServiceHttpClient: RestClient
        private set

    lateinit var accountServiceHttpClient: RestClient
        private set

    lateinit var payeeServiceHttpClient: RestClient
        private set

    lateinit var paymentsServiceHttpClient: RestClient
        private set

    lateinit var standingOrderServiceHttpClient: RestClient
        private set

    lateinit var directDebitServiceHttpClient: RestClient
        private set

    lateinit var sandboxServiceHttpClient: RestClient
        private set

    lateinit var cardServiceHttpClient: RestClient
        private set

    lateinit var mdrServiceHttpClient: RestClient
        private set

    lateinit var copilotServiceHttpClient: RestClient
        private set

    lateinit var amlServiceHttpClient: RestClient
        private set

    lateinit var malaiStubHttpClient: RestClient
        private set

    /**
     * Plain RestClient pointed at auth-service, WITHOUT the [MalaiHttpInteractionCapturer]
     * interceptor. Used for `given`-phase preconditions (e.g. registering a user in auth-service
     * so a login-style test has something to log into) that should NOT show up on the
     * Kensa sequence diagram alongside the test's main `when` flow.
     */
    lateinit var preconditionsAuthServiceHttpClient: RestClient
        private set

    /**
     * Plain RestClient pointed at account-holder-service, WITHOUT the [MalaiHttpInteractionCapturer]
     * interceptor. Used for `given`-phase preconditions (e.g. creating an account holder and
     * individual so a login-style test has something to look up) that should NOT show up on the
     * Kensa sequence diagram alongside the test's main `when` flow.
     */
    lateinit var preconditionsAccountHolderServiceHttpClient: RestClient
        private set

    /**
     * Plain RestClient pointed at account-service, WITHOUT the [MalaiHttpInteractionCapturer]
     * interceptor. Used for `given`-phase preconditions (e.g. creating an account and seeding a
     * balance via API) that should NOT show up on the Kensa sequence diagram alongside the test's
     * main `when` flow.
     */
    lateinit var preconditionsAccountServiceHttpClient: RestClient
        private set

    /**
     * Plain RestClient pointed at payee-service, WITHOUT the [MalaiHttpInteractionCapturer]
     * interceptor. Used for `given`-phase preconditions that should NOT show up on the
     * Kensa sequence diagram alongside the test's main `when` flow.
     */
    lateinit var preconditionsPayeeServiceHttpClient: RestClient
        private set

    /**
     * Plain RestClient pointed at payments-service, WITHOUT the [MalaiHttpInteractionCapturer]
     * interceptor. Used for `given`-phase preconditions that should NOT show up on the
     * Kensa sequence diagram alongside the test's main `when` flow.
     */
    lateinit var preconditionsPaymentsServiceHttpClient: RestClient
        private set

    /**
     * Plain RestClient pointed at sandbox-service, WITHOUT the [MalaiHttpInteractionCapturer]
     * interceptor. Used for `given`-phase preconditions that should NOT show up on the
     * Kensa sequence diagram alongside the test's main `when` flow.
     */
    lateinit var preconditionsSandboxServiceHttpClient: RestClient
        private set

    /**
     * Plain RestClient pointed at malai-stub, WITHOUT the [MalaiHttpInteractionCapturer]
     * interceptor. Used to poll the stub's recorded FPS return-to-sender list
     * (`GET /test-hooks/fps/returns`) from [WithMalaiStub] — the fetch itself must NOT show up
     * as a Client → MalaiStub arrow.
     */
    lateinit var preconditionsMalaiStubHttpClient: RestClient
        private set

    val isInitialized: Boolean get() = ::authServiceHttpClient.isInitialized

    /**
     * Start the WireMock server, wire up a [RestClient] pointed at each service's Docker-published
     * port (with a [MalaiHttpInteractionCapturer] so each top-level test call renders on the Kensa
     * sequence diagram), install the inter-service reverse-proxy stubs, and create a [JdbcTemplate]
     * for the e2e PostgreSQL database.
     *
     * Called exactly once per JVM from [com.malai.test.e2e.MalaiE2ETest]'s companion-object init block.
     */
    /**
     * Maps a WireMock [ServeEvent][com.github.tomakehurst.wiremock.stubbing.ServeEvent] onto the
     * malai party that initiated the request, by reading the `X-Malai-Source` header set by
     * [AbstractMalaiClient][com.malai.client.malai.AbstractMalaiClient] (for HTTP clients) and
     * [MalaiSourceAwsSdkInterceptorConfig][com.malai.security.cognito.tracking.MalaiSourceAwsSdkInterceptorConfig]
     * (for AWS SDK Cognito calls). Shared between the Kensa sequence-diagram capturer AND the
     * [MalaiE2ETest.theFollowingHttpRequestWasMade] predicate-based assertion helper.
     */
    val fromSystemLookup: FromSystemLookup = FromSystemLookup { serveEvent ->
        // Only Cognito-shaped traffic still reaches WireMock after phase 2c, so the only sources
        // we expect on a captured ServeEvent are service identities (auth-service / ... ) calling
        // out to Cognito. The malai-stub branch is dead but harmless — kept symmetrical with the
        // envelope-side [KensaEventConsumer.partyFromSource] in case future Cognito-stub paths
        // pass through that container.
        val malaiSource = serveEvent.request
            .takeIf { it.containsHeader("X-Malai-Source") }
            ?.header("X-Malai-Source")
            ?.firstValue()
        when (malaiSource) {
            "auth-service" -> AuthService
            "account-holder-service" -> AccountHolderService
            "account-service" -> AccountService
            "ledger-service" -> LedgerService
            "payee-service" -> PayeeService
            "payments-service" -> PaymentsService
            "standing-order-service" -> StandingOrderService
            "direct-debit-service" -> DirectDebitService
            "insights-service" -> InsightsService
            "sandbox-service" -> SandboxService
            "card-service" -> CardService
            "mdr-service" -> MdrService
            "copilot-service" -> CopilotService
            "aml-service" -> AmlService
            "card-gateway" -> CardGateway
            "fps-gateway" -> FpsGateway
            "swift-gateway" -> SwiftGateway
            "bacs-gateway" -> BacsGateway
            "malai-stub" -> MalaiStub
            "savings-goal-service" -> MalaiParty.SavingsGoalService
            "transaction-feed-service" -> MalaiParty.TransactionFeedService
            else -> MalaiParty.Unknown
        }
    }

    /**
     * Maps a URL onto the malai party being called. Path is what matters — host is stripped by
     * callers. Used by:
     *  - [WireMockInteractionCapturer]: it only sees Cognito-shaped paths now, so the catchall
     *    `Cognito` branch is what fires (the inter-service entries are dead code there).
     *  - [KensaEventConsumer]: envelopes carry the full URL; `URI(url).path` is what the consumer
     *    passes here — that's how inter-service calls get their `to` party.
     *  - [MalaiE2ETest.theFollowingHttpRequestWasMade] / `…WereMade` assertions.
     *
     * Ordered most-specific-first where prefixes overlap (e.g. `/api/v1/internal/onboarding/...`
     * before `/api/v1/internal/...`).
     */
    val toSystemLookup: PartyLookupByUrl = PartyLookupByUrl { url ->
        when {
            // === Endpoint paths inside malai-api ===
            url.startsWith("/api/v1/internal/onboarding/account-holders") -> AccountHolderService
            url.startsWith("/api/v1/internal/onboarding/accounts") -> AccountService
            url.startsWith("/api/v1/internal/accounts") -> AccountService
            url.startsWith("/api/v1/internal/cards") -> CardService
            url.startsWith("/api/v1/internal/ledger") -> LedgerService
            url.startsWith("/api/v1/internal/standing-orders") -> StandingOrderService
            url.startsWith("/api/v1/internal/direct-debits") -> DirectDebitService
            url.startsWith("/api/v1/internal/insights") -> InsightsService
            url.startsWith("/api/v1/internal/payments") -> PaymentsService
            url.startsWith("/api/v1/internal/mdr") -> MdrService
            url.startsWith("/api/v1/account-holder") -> AccountHolderService
            url.startsWith("/api/v1/addresses") -> AccountHolderService
            url.startsWith("/api/v1/settle-up") -> AccountHolderService
            url.startsWith("/api/v1/accounts") -> AccountService
            url.startsWith("/api/v1/account/") -> AccountService
            url.startsWith("/api/v1/auth") -> AuthService
            url.startsWith("/api/v1/user") -> AuthService
            url.startsWith("/api/v1/admin/mdr") -> MdrService
            url.startsWith("/api/v1/admin/copilot") -> CopilotService
            url.startsWith("/api/v1/admin/aml") -> AmlService
            url.startsWith("/api/v1/admin") -> AuthService
            url.startsWith("/api/v1/payments") -> PaymentsService
            url.startsWith("/api/v1/payees") -> PayeeService
            url.startsWith("/api/v1/confirmation-of-payee") -> AccountService
            url.startsWith("/api/v1/standing-orders") -> StandingOrderService
            url.startsWith("/api/v1/direct-debits") -> DirectDebitService
            url.startsWith("/api/v1/credit-card-applications") -> AccountService
            url.startsWith("/api/v1/cards") -> CardService
            url.startsWith("/api/v1/sandbox") -> SandboxService
            // === Gateway callbacks inside malai-api ===
            url.startsWith("/fps/callbacks/") -> FpsGateway
            url.startsWith("/swift/callbacks/") -> SwiftGateway
            url.startsWith("/bacs/callbacks/") -> BacsGateway
            url.startsWith("/cards/callbacks/") -> CardGateway
            // === Endpoints inside malai-stub (impersonating external schemes) ===
            url.startsWith("/fps/payments") || url.startsWith("/fps/returns") -> Fps
            url.startsWith("/swift/payments") -> Swift
            url.startsWith("/cop/") -> CoP
            // The scenario-inbound triggers are the test acting AS the scheme — same role
            // malai-stub plays when it posts to a gateway callback. Attribute them to
            // the scheme party, not the stub. All of /scenarios/cards/* is likewise the card
            // network's own surface (merchant directory, PAN registry, origination triggers), so
            // it reads as CardScheme. The cross-rail /scenarios/counterparties catalogue is the
            // industry directory of external-bank accounts, so it reads as CounterpartyDirectory.
            // Only /test-hooks/* (arm-return, list-returns, …) is genuine stub control surface and
            // stays on MalaiStub.
            url.startsWith("/scenarios/inbound/fps") -> Fps
            url.startsWith("/scenarios/inbound/bacs") -> Bacs
            url.startsWith("/scenarios/cards/") -> CardScheme
            url.startsWith("/scenarios/counterparties") -> CounterpartyDirectory
            url.startsWith("/scenarios/") -> MalaiStub
            url.startsWith("/test-hooks/") -> MalaiStub
            // === Identity / OAuth2 stub paths (still on WireMock) ===
            url.startsWith("$OAUTH2_SERVICE_PROXY_PATH/") -> OAuth2Service
            // Catchall — JWKS (`/identity-provider/...`) and bare-path identity-provider SDK calls
            else -> IdentityProvider
        }
    }

    fun boot() {
        Kensa.konfigure { titleText = "Malai E2E Tests" }

        if (isInitialized) return

        WireMockHolder.set(
            options()
                .port(WIREMOCK_PORT)
                .extensions(
                    WireMockRequestReferenceHandler(),
                    WireMockInteractionCapturer(
                        fromSystemLookup = fromSystemLookup,
                        toSystemLookup = toSystemLookup,
                        // Inter-service HTTP no longer reaches WireMock at all (phase 2c — services
                        // talk container-to-container directly). The only traffic WireMock still
                        // observes is Cognito-stubbed: the OIDC issuer + JWKS endpoint, the SDK
                        // bare-path SignUp/InitiateAuth calls, and the service-token endpoint. We
                        // hide the service-token endpoint from the diagram — same intent as before,
                        // it's plumbing, not a business interaction.
                        shouldCapture = { serveEvent ->
                            !serveEvent.request.url.startsWith("$OAUTH2_SERVICE_PROXY_PATH/")
                        },
                    ),
                )
                .let { WireMockServer(it) }
                .apply { start() }
        )

        authServiceHttpClient = restClientFor(AUTH_SERVICE_URL, AuthService)
        accountHolderServiceHttpClient = restClientFor(ACCOUNT_HOLDER_SERVICE_URL, AccountHolderService)
        accountServiceHttpClient = restClientFor(ACCOUNT_SERVICE_URL, AccountService)
        payeeServiceHttpClient = restClientFor(PAYEE_SERVICE_URL, PayeeService)
        paymentsServiceHttpClient = restClientFor(PAYMENTS_SERVICE_URL, PaymentsService)
        standingOrderServiceHttpClient = restClientFor(STANDING_ORDER_SERVICE_URL, StandingOrderService)
        directDebitServiceHttpClient = restClientFor(DIRECT_DEBIT_SERVICE_URL, DirectDebitService)
        sandboxServiceHttpClient = restClientFor(SANDBOX_SERVICE_URL, SandboxService)
        cardServiceHttpClient = restClientFor(CARD_SERVICE_URL, CardService)
        mdrServiceHttpClient = restClientFor(MDR_SERVICE_URL, MdrService)
        copilotServiceHttpClient = restClientFor(COPILOT_SERVICE_URL, CopilotService)
        amlServiceHttpClient = restClientFor(AML_SERVICE_URL, AmlService)
        malaiStubHttpClient = restClientFor(MALAI_STUB_URL, MalaiStub)

        preconditionsAuthServiceHttpClient = plainRestClient(AUTH_SERVICE_URL)
        preconditionsAccountHolderServiceHttpClient = plainRestClient(ACCOUNT_HOLDER_SERVICE_URL)
        preconditionsAccountServiceHttpClient = plainRestClient(ACCOUNT_SERVICE_URL)
        preconditionsPayeeServiceHttpClient = plainRestClient(PAYEE_SERVICE_URL)
        preconditionsPaymentsServiceHttpClient = plainRestClient(PAYMENTS_SERVICE_URL)
        preconditionsSandboxServiceHttpClient = plainRestClient(SANDBOX_SERVICE_URL)
        preconditionsMalaiStubHttpClient = plainRestClient(MALAI_STUB_URL)

        bootKensaEventBus()
    }

    private const val KENSA_EVENTS_QUEUE_NAME: String = "kensa-events.fifo"

    val sqsAsyncClient: SqsAsyncClient = SqsAsyncClient.builder()
        .endpointOverride(URI.create(LOCALSTACK_URL))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build()

    lateinit var kensaEventsQueueUrl: String
        private set

    lateinit var kensaEventConsumer: KensaEventConsumer
        private set

    private fun bootKensaEventBus() {
        kensaEventsQueueUrl = sqsAsyncClient.createQueue { req ->
            req.queueName(KENSA_EVENTS_QUEUE_NAME)
                .attributes(mapOf(
                    QueueAttributeName.FIFO_QUEUE to "true",
                    QueueAttributeName.CONTENT_BASED_DEDUPLICATION to "false",
                ))
        }.get().queueUrl()
        kensaEventConsumer = KensaEventConsumer(sqsAsyncClient, kensaEventsQueueUrl).also { it.start() }
        Runtime.getRuntime().addShutdownHook(Thread { kensaEventConsumer.stop() })
    }

    fun publishTestEnded(testId: String) {
        val envelope = KensaEventEnvelope(
            testId = testId,
            kind = KensaEventKind.TEST_ENDED,
            tsEpochMillis = System.currentTimeMillis(),
        )
        val body = testingObjectMapper.writeValueAsString(envelope)
        sqsAsyncClient.sendMessage { req ->
            req.queueUrl(kensaEventsQueueUrl)
                .messageBody(body)
                .messageGroupId(testId)
                .messageDeduplicationId("$testId-TEST_ENDED-${System.nanoTime()}")
        }.get()
    }

    /**
     * The `serviceParty` arg is kept for call-site readability — every existing call site already
     * names the service it expects to talk to. The actual `to` party on the diagram is derived
     * from the request URL path by [toSystemLookup] when the consumer drains the envelope, so
     * the value isn't used here.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun restClientFor(baseUrl: String, serviceParty: Party): RestClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(noCookieRequestFactory())
            .requestInterceptor(
                KensaEnvelopePublishingHttpInterceptor(
                    sqsAsyncClient = sqsAsyncClient,
                    kensaEventsQueueUrlProvider = { kensaEventsQueueUrl },
                )
            )
            .prettyPrintRequestBody()
            .doNotThrowOnNon2xxResponseStatus()
            .build()

    private fun plainRestClient(baseUrl: String): RestClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(noCookieRequestFactory())
            .prettyPrintRequestBody()
            .build()

    /**
     * Build a request factory whose underlying HttpClient does not maintain a cookie store.
     *
     * Spring's `RestClient` picks Apache HttpComponents 5 when it is on the classpath, and
     * `HttpClientBuilder.create()` enables `BasicCookieStore` by default. With auth-service
     * now setting HttpOnly session cookies on `/login` and `/refresh-token`, that default
     * would silently flow cookies from one e2e test method into the next via these
     * JVM-shared `RestClient`s — exactly the leak that broke 13 acceptance tests in batch
     * when B1 was first introduced (see `MalaiAcceptanceTestContext`). Disabling cookie
     * management makes each request a pure function of the headers the test sets, so the
     * cookie-flow test in `AuthCookieTest` has to thread `Cookie:` headers explicitly —
     * which is exactly what we want to verify.
     */
    private fun noCookieRequestFactory(): HttpComponentsClientHttpRequestFactory =
        HttpComponentsClientHttpRequestFactory(
            HttpClientBuilder.create().disableCookieManagement().build()
        )

    private fun RestClient.Builder.doNotThrowOnNon2xxResponseStatus() =
        defaultStatusHandler({ _ -> true }, { _, _ -> })

    private fun RestClient.Builder.prettyPrintRequestBody() = apply {
        messageConverters { converters ->
            converters.removeIf { it is JacksonJsonHttpMessageConverter }
            converters.add(JacksonJsonHttpMessageConverter(testingObjectMapper))
        }
    }
}
