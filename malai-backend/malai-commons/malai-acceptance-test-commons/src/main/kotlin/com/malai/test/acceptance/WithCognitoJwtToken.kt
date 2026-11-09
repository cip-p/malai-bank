package com.malai.test.acceptance

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.malai.security.SecurityConfig.Companion.ACCOUNT_HOLDER_ID_HEADER
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import com.malai.test.acceptance.WithIdentityProvider.Companion.stubbedCognitoSigner
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.commons.support.jwt.JwtTokenBuilder.Companion.userJwtTokenBuilderWithDefaultValues
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

/**
 * Generates Cognito-signed JWT access tokens for use in acceptance-test HTTP requests.
 * The token is signed with the same stubbed Cognito signer whose public key is served
 * by [WithIdentityProvider.primeJwksStub], so the running service accepts it as genuine.
 */
interface WithCognitoJwtToken : WithIdentityProvider {

    fun generateAccessTokenForUserId(userId: UUID, cognitoGroups: List<String> = listOf("customer")): String =
        signedJwtFor(
            userJwtTokenBuilderWithDefaultValues()
                .withUserId(userId)
                .withCognitoGroups(cognitoGroups.takeIf { it.isNotEmpty() })
                .build()
        )

    fun generateExpiredAccessTokenForUserId(userId: UUID, cognitoGroups: List<String> = listOf("customer")): String =
        signedJwtFor(
            userJwtTokenBuilderWithDefaultValues()
                .withUserId(userId)
                .withCognitoGroups(cognitoGroups.takeIf { it.isNotEmpty() })
                .withTtlMinutes(-60)
                .build()
        )

    fun generateRefreshTokenForUserId(userId: UUID): String =
        signedJwtFor(
            userJwtTokenBuilderWithDefaultValues()
                .withUserId(userId)
                .withType("refresh")
                // 30-day TTL — Cognito's default `RefreshTokenValidity`; the value is
                // ignored by the auth-service stack but matches what real clients see.
                .withTtlMinutes(30 * 24 * 60)
                .build()
        )

    private fun signedJwtFor(claims: Map<String, Any>): String {
        val privateKey = stubbedCognitoSigner.getKeyPair().private as RSAPrivateKey
        val publicKey = stubbedCognitoSigner.getKeyPair().public as RSAPublicKey
        val algorithm: Algorithm = Algorithm.RSA256(publicKey, privateKey)

        val headerClaims = mapOf<String, Any>(
            "kid" to stubbedCognitoSigner.getKeyId(),
            "alg" to "RS256"
        )

        return JWT.create()
            .withHeader(headerClaims)
            .withIssuer(identityProviderIssuerUrl())
            .withPayload(claims)
            .sign(algorithm)
    }

    fun HttpRequest.HttpRequestBuilder.withCustomerJwtTokenForUserId(
        userId: UUID,
    ) = this.withJwtTokenForUserId(userId, listOf("customer"))

    fun HttpRequest.HttpRequestBuilder.withJwtTokenForUserId(
        userId: UUID,
        cognitoGroups: List<String>,
    ) = apply {
        header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId, cognitoGroups)}")
    }

    fun HttpRequest.HttpRequestBuilder.withCustomerJwtTokenForUserIdAndAccountHolderId(
        userId: UUID,
        accountHolderId: UUID,
    ) = this.withJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId, listOf("customer"))

    fun HttpRequest.HttpRequestBuilder.withJwtTokenForUserIdAndAccountHolderId(
        userId: UUID,
        accountHolderId: UUID,
        cognitoGroups: List<String>,
    ) = apply {
        header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId, cognitoGroups)}")
        header(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
    }
}
