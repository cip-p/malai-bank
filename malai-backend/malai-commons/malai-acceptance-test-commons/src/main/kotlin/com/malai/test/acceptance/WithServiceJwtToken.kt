package com.malai.test.acceptance

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import com.malai.test.acceptance.WithIdentityProvider.Companion.stubbedCognitoSigner
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.commons.support.jwt.JwtTokenBuilder.Companion.serviceJwtTokenBuilderWithDefaultValues
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Generates Cognito service (client_credentials) access tokens for use in acceptance-test
 * HTTP requests against internal endpoints. The token is signed with the same
 * stubbed Cognito signer as user tokens, so the running service accepts it as genuine.
 */
interface WithServiceJwtToken : WithIdentityProvider {

    fun generateServiceAccessToken(
        clientId: String = "test-service-client-id",
        scopes: List<String> = listOf("malai-internal/onboarding.write"),
    ): String {
        val privateKey = stubbedCognitoSigner.getKeyPair().private as RSAPrivateKey
        val publicKey = stubbedCognitoSigner.getKeyPair().public as RSAPublicKey
        val algorithm: Algorithm = Algorithm.RSA256(publicKey, privateKey)

        val headerClaims = mapOf<String, Any>(
            "kid" to stubbedCognitoSigner.getKeyId(),
            "alg" to "RS256",
        )

        val claims = serviceJwtTokenBuilderWithDefaultValues()
            .withClientId(clientId)
            .withScopes(scopes)
            .build()

        return JWT.create()
            .withHeader(headerClaims)
            .withIssuer(identityProviderIssuerUrl())
            .withPayload(claims)
            .sign(algorithm)
    }

    fun HttpRequest.HttpRequestBuilder.withServiceJwtToken(
        clientId: String = "test-service-client-id",
        scopes: List<String> = listOf("malai-internal/onboarding.write"),
    ) = apply {
        header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateServiceAccessToken(clientId, scopes)}")
    }
}
