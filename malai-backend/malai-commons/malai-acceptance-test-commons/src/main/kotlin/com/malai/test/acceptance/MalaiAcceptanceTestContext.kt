package com.malai.test.acceptance

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.malai.test.acceptance.MalaiAcceptanceTestContext.boot
import com.malai.test.acceptance.kensa.interaction.MalaiHttpInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.PartyLookupByUrl
import com.malai.test.acceptance.kensa.interaction.WireMockInteractionCapturer
import com.malai.test.acceptance.kensa.misc.WireMockRequestReferenceHandler
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Kensa
import dev.kensa.state.Party
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.getProperty
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import kotlin.properties.Delegates

/**
 * Singleton holder of the shared infrastructure for a malai-service acceptance-test run:
 *
 * - the [WireMockServer] used to stub downstream dependencies
 * - the [ConfigurableApplicationContext] of the service under test
 * - a [RestClient] pointed at the running app, with a [MalaiHttpInteractionCapturer] so every
 *   outgoing request/response ends up on the Kensa sequence diagram
 *
 * A service's acceptance-test base class bootstraps this context exactly once, at JVM class-load
 * time, by calling [boot] from a `companion object { init { } }` block. See
 * `malai-acceptance-test-commons/README` or the per-service `AuthAcceptanceTest` /
 * `AccountHolderAcceptanceTest` subclasses for the minimal example.
 */
object MalaiAcceptanceTestContext {

    // Tests default to the dedicated `malai_test` database — `start-docker-containers-local.sh`
    // creates it, `malai-db.sh` wipes + migrates it. The `malai_local` database is reserved
    // for `start-malai-api-local.sh` (webapp dev) and is never touched by the test suite.
    val DATABASE_URL: String = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/malai_test"

    val wireMockServer: WireMockServer get() = WireMockHolder.server

    lateinit var applicationContext: ConfigurableApplicationContext
        private set

    lateinit var httpClient: RestClient
        private set

    var port: Int by Delegates.notNull()
        private set

    val isInitialized: Boolean get() = ::applicationContext.isInitialized

    /**
     * Start the WireMock server with the given downstream-lookup, boot the Spring app, and wire
     * up a [RestClient] pointed at the running app. Called exactly once per JVM from the
     * acceptance-test base class of each service.
     *
     * @param reportTitle the title shown on this service's Kensa report (e.g.
     *        `"Account Service Acceptance Tests"`). Each module runs in its own test JVM, so the
     *        title is per-service.
     * @param fromSystem the [Party] representing the service under test on the Kensa sequence
     *        diagram (e.g. `AuthService`, `AccountHolderService`).
     * @param toSystemLookup maps a WireMock-stubbed URL to the [Party] of the downstream dependency
     *        being stubbed (e.g. a Cognito URL → `Cognito`).
     * @param bootApp lambda that takes the started WireMock server and returns a fully booted
     *        Spring [ConfigurableApplicationContext]. The lambda is where each service threads
     *        its own `wiremock.url(...)` values into its own `runXxxService(...)` boot call.
     */
    fun boot(
        reportTitle: String,
        fromSystem: Party,
        toSystemLookup: PartyLookupByUrl,
        bootApp: (WireMockServer) -> ConfigurableApplicationContext,
    ) {
        System.setProperty("aws.accessKeyId", "test")
        System.setProperty("aws.secretAccessKey", "test")

        Kensa.konfigure { titleText = reportTitle }

        if (isInitialized) return

        WireMockHolder.set(
            options()
                .port(0)
                .extensions(
                    WireMockRequestReferenceHandler(),
                    WireMockInteractionCapturer(
                        fromSystem = fromSystem,
                        toSystemLookup = toSystemLookup,
                    ),
                )
                .let { WireMockServer(it) }
                .apply { start() }
        )

        applicationContext = bootApp(wireMockServer)

        port = applicationContext.environment.getProperty<Int>("local.server.port")
            ?: throw RuntimeException("Unable to find local server port")

        httpClient = RestClient.builder()
            .baseUrl("http://localhost:$port")
            .requestFactory(noCookieRequestFactory())
            .requestInterceptor(MalaiHttpInteractionCapturer(serviceParty = fromSystem))
            .prettyPrintRequestBody()
            .doNotThrowOnNon2xxResponseStatus()
            .build()
    }

    private fun RestClient.Builder.doNotThrowOnNon2xxResponseStatus() =
        defaultStatusHandler({ _ -> true }, { _, _ -> })

    /**
     * Build a request factory whose underlying HttpClient does not maintain a cookie store.
     *
     * Spring's `RestClient.builder().build()` picks Apache HttpComponents 5 when it is on
     * the classpath (it is — `malai-clients-common` depends on httpclient5), and the default
     * `HttpClientBuilder.create()` enables `BasicCookieStore`. That default leaks `Set-Cookie`
     * responses from one test method into the next via this singleton's shared `RestClient`,
     * which is exactly the cookie-leak that broke 13 auth-service tests in batch when B1's
     * HttpOnly session cookies were first introduced — `login` set `access_token` and every
     * subsequent test's Authorization header was overridden by the stale cookie. Disabling
     * cookie management makes each test's HTTP request a pure function of what the test
     * itself set on the request builder.
     */
    private fun noCookieRequestFactory(): HttpComponentsClientHttpRequestFactory =
        HttpComponentsClientHttpRequestFactory(
            HttpClientBuilder.create().disableCookieManagement().build()
        )

    private fun RestClient.Builder.prettyPrintRequestBody() = apply {
        messageConverters { converters ->
            converters.removeIf { it is JacksonJsonHttpMessageConverter }
            converters.add(JacksonJsonHttpMessageConverter(testingObjectMapper))
        }
    }
}
