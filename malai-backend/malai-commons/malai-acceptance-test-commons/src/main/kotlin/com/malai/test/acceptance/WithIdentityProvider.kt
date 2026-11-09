package com.malai.test.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.test.commons.support.jwt.StubbedCognitoSigner

/**
 * Primes the WireMock stub that serves the identity provider's JWKS endpoint. The running service
 * fetches the public key from this endpoint to verify incoming JWTs; any test that sends a
 * signed token (see [WithCognitoJwtToken]) relies on this stub being primed.
 */
interface WithIdentityProvider : WithWiremock {

    fun identityProviderIssuerUrl(): String = wiremock().url(CONTEXT_PATH)

    fun primeJwksStub() {
        wiremock().stubFor(
            get(urlEqualTo("$CONTEXT_PATH/.well-known/jwks.json"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{ "keys": [${stubbedCognitoSigner.getJwk().toPublicJWK()} ]}"""
                        )
                )
        )
    }

    companion object {
        const val CONTEXT_PATH = "/identity-provider"
        val stubbedCognitoSigner = StubbedCognitoSigner()
    }
}
