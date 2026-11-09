package com.malai.cognitostub

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.slf4j.LoggerFactory

class MalaiCognitoStubServer(private val settings: StubSettings) {

    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var wireMock: WireMockServer

    lateinit var issuer: String
        private set

    val port: Int
        get() = wireMock.port()

    fun baseUrl(): String = "http://localhost:$port"

    fun start(): MalaiCognitoStubServer {
        val signingKey = SigningKey.loadOrGenerate(settings.stateDir.resolve("signing-key.jwk"))
        val users = UserDirectory(settings.stateDir.resolve("users.json"))
        val tokens = TokenMinter(signingKey, issuerProvider = { issuer })

        val configuration = wireMockConfig()
            .notifier(Slf4jNotifier(false))
            .maxRequestJournalEntries(500)
            .extensions(CognitoIdpTransformer(users, tokens), OAuth2TokenTransformer(tokens), StubControlTransformer(users))
        if (settings.port == 0) configuration.dynamicPort() else configuration.port(settings.port)

        wireMock = WireMockServer(configuration)
        wireMock.start()
        issuer = settings.issuer ?: "${baseUrl()}/${StubSettings.DEFAULT_USER_POOL_ID}"
        registerRoutes(signingKey)

        log.info("malai Cognito stub listening on {} (issuer {}, state in {}, {} known users)", baseUrl(), issuer, settings.stateDir, users.all().size)
        return this
    }

    fun stop() {
        wireMock.stop()
    }

    private fun registerRoutes(signingKey: SigningKey) {
        wireMock.stubFor(
            post(urlPathMatching("/?"))
                .withHeader(AwsJson.TARGET_HEADER, matching("AWSCognitoIdentityProviderService\\..+"))
                .willReturn(aResponse().withTransformers(CognitoIdpTransformer.NAME))
        )
        wireMock.stubFor(
            get(urlPathMatching("(/.*)?/\\.well-known/jwks\\.json"))
                .willReturn(okJson(signingKey.jwks()))
        )
        wireMock.stubFor(
            post(urlPathMatching("(/.*)?/oauth2/token"))
                .willReturn(aResponse().withTransformers(OAuth2TokenTransformer.NAME))
        )
        wireMock.stubFor(
            post(urlPathEqualTo("/stub/reset"))
                .willReturn(aResponse().withTransformers(StubControlTransformer.NAME))
        )
        wireMock.stubFor(
            get(urlPathEqualTo("/stub/users"))
                .willReturn(aResponse().withTransformers(StubControlTransformer.NAME))
        )
    }
}
