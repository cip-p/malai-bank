package com.malai.test.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.client.malai.ServiceTokenClient
import dev.kensa.Action
import dev.kensa.GivensContext
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * WireMock stub for the OAuth2 service-token endpoint. Each malai service's `ServiceTokenClient`
 * calls `POST /oauth2/token` with `grant_type=client_credentials` and gets back a JWT signed by
 * the same stubbed signer used for user tokens.
 *
 * The registered [ServiceTokenEvictionExtension] drops every [ServiceTokenClient]'s in-memory
 * token cache before each test so the OAuth2 round-trip shows up on the Kensa sequence diagram
 * every time, not just on the first test in the JVM.
 */
@ExtendWith(WithOAuth2Service.ServiceTokenEvictionExtension::class)
interface WithOAuth2Service : WithWiremock, WithServiceJwtToken {

    fun oauth2ServiceTokenEndpointReturnsToken(
        clientId: String = "test-service-client-id",
        scopes: List<String> = listOf("malai-internal/onboarding.write"),
        expiresInSeconds: Int = 3600,
    ) = Action<GivensContext> {
        val token = generateServiceAccessToken(clientId, scopes)
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/oauth2/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "access_token": "$token",
                              "expires_in": $expiresInSeconds,
                              "token_type": "Bearer"
                            }
                            """.trimIndent()
                        )
                )
        )
    }

    class ServiceTokenEvictionExtension : BeforeEachCallback {
        override fun beforeEach(extensionContext: ExtensionContext) {
            if (!MalaiAcceptanceTestContext.isInitialized) return
            MalaiAcceptanceTestContext.applicationContext
                .getBeansOfType(ServiceTokenClient::class.java)
                .values
                .forEach { it.evict() }
        }
    }

    companion object {
        const val CONTEXT_PATH = "/oauth2-service"
    }
}
