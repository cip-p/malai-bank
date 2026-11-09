package com.malai.test.commons.support.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.malai.security.cognito.CognitoJwtConfigurationProperties
import com.malai.test.commons.support.jwt.JwtTokenBuilder.Companion.userJwtTokenBuilderWithDefaultValues
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*


@Component
class CognitoJwtTokenSupport(
    private val stubbedCognitoSigner: StubbedCognitoSigner,
    private val cognitoJwtConfigurationProperties: CognitoJwtConfigurationProperties
) {

    fun generateJwtTokenForUserId(userId: UUID): String {
        return generateJwtToken(userJwtTokenBuilderWithDefaultValues()
            .withUserId(userId))
    }

    fun generateJwtTokenForUserIdAndAccountHolderId(userId: UUID, accountHolderId: UUID): String {
        return generateJwtToken(userJwtTokenBuilderWithDefaultValues()
            .withUserId(userId)
            .withAccountHolderId(accountHolderId))
    }

    fun generateJwtToken(jwtTokenBuilder: JwtTokenBuilder): String {

        val privateKey = stubbedCognitoSigner.getKeyPair().private as RSAPrivateKey
        val publicKey = stubbedCognitoSigner.getKeyPair().public as RSAPublicKey
        val algorithm: Algorithm = Algorithm.RSA256(publicKey, privateKey)

        val issuer = cognitoJwtConfigurationProperties.issuer

        val headerClaims: MutableMap<String, Any> = HashMap<String, Any>()
        headerClaims["kid"] = stubbedCognitoSigner.getKeyId()
        headerClaims["alg"] = "RS256"

        val claims = jwtTokenBuilder.build()

        return JWT.create()
            .withHeader(headerClaims)
            .withIssuer(issuer)
            .withPayload(claims)
            .sign(algorithm)
    }
}
