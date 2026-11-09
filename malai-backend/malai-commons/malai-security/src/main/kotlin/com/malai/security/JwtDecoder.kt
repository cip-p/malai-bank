package com.malai.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.malai.commons.exception.InvalidJwtTokenException
import com.malai.commons.exception.MalaiCode.*
import com.malai.security.cognito.CognitoJwtConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*


@Component
class JwtDecoder(
    awsCognitoRSAKeyProvider: AwsCognitoRSAKeyProvider?,
    cognitoJwtConfigurationProperties: CognitoJwtConfigurationProperties
) {
    private val jwtVerifier: JWTVerifier

    private val ACCEPTED_LEEWAY_IN_SECONDS: Long = 60
    private val log = LoggerFactory.getLogger(this.javaClass)

    init {
        val algorithm = Algorithm.RSA256(awsCognitoRSAKeyProvider)
        val issuer = cognitoJwtConfigurationProperties.issuer
        jwtVerifier = JWT.require(algorithm)
            .acceptLeeway(ACCEPTED_LEEWAY_IN_SECONDS)
            .withIssuer(issuer)
            .build()
    }

    fun decodeAndVerify(token: String): DecodedJWT {
        val decodedJwt = decode(token)
        val userId = decodedJwt.subject ?: throw InvalidJwtTokenException(JWT_MISSING_USER_ID)

        if (decodedJwt.expiresAt.before(Date())) {
            log.error("expired JWT token for user {}", userId)
            throw InvalidJwtTokenException(JWT_EXPIRED)
        }

        // Standard OAuth2 / Cognito flow: clients send the access_token as
        // `Authorization: Bearer ...`. The id_token (if needed for identity
        // claims) stays on the client. Cognito access_tokens carry `sub`
        // (user id) which is all the rest of the stack uses.
        val tokenType = decodedJwt.getClaim("token_use")
        if (tokenType == null || tokenType.asString() != "access") {
            log.error("JWT is not an access token, it is '{}' for user {}", tokenType?.asString(), userId)
            throw InvalidJwtTokenException(JWT_NOT_ACCESS_TOKEN)
        }

        verifyToken(decodedJwt, userId)
        return decodedJwt
    }

    private fun decode(token: String): DecodedJWT {
        return try {
            JWT.decode(token)
        } catch (e: JWTDecodeException) {
            log.error("Error decoding JWT token", e)
            throw InvalidJwtTokenException(JWT_CANT_DECODE)
        }
    }

    private fun verifyToken(decodedJwt: DecodedJWT, userId: String) {
        try {
            jwtVerifier.verify(decodedJwt)
        } catch (e: JWTVerificationException) {
            log.error("Error verifying JWT token for userId {}", userId, e)
            throw InvalidJwtTokenException(JWT_CANT_VERIFY)
        }
    }
}
