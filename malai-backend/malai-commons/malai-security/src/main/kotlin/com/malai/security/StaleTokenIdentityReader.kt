package com.malai.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.malai.commons.exception.InvalidJwtTokenException
import com.malai.commons.exception.JwtProviderException
import com.malai.commons.exception.MalaiCode.JWT_CANT_DECODE
import com.malai.commons.exception.MalaiCode.JWT_CANT_VERIFY
import com.malai.commons.exception.MalaiCode.JWT_MISSING_USER_ID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Extracts the `sub` (user id) from a Cognito access-token JWT whose RSA signature verifies
 * against the Cognito JWKS but whose expiry is **deliberately ignored**. An expired token is
 * still authentic evidence of *who*, even though it no longer authorizes *anything* — see
 * auth-service ADR-0002. `refresh-token` uses this to derive the subject when the now-optional
 * `userId` is absent, reading the expired `access_token` cookie on a cold browser load.
 *
 * Signature verification is non-negotiable (ADR-0002): without it any well-formed JWT could
 * name an arbitrary `sub`. Anything that fails to decode, fails signature verification, or
 * carries no usable subject is rejected as unauthorized.
 */
@Component
class StaleTokenIdentityReader(
    private val keyProvider: RSAKeyProvider?,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun readSubject(token: String): UUID {
        val decoded = decode(token)
        verifySignature(decoded)
        val subject = decoded.subject ?: throw InvalidJwtTokenException(JWT_MISSING_USER_ID)
        return parseUserId(subject)
    }

    private fun decode(token: String): DecodedJWT =
        try {
            JWT.decode(token)
        } catch (e: JWTDecodeException) {
            log.error("could not decode stale access token", e)
            throw InvalidJwtTokenException(JWT_CANT_DECODE)
        }

    private fun verifySignature(decoded: DecodedJWT) {
        try {
            Algorithm.RSA256(keyProvider).verify(decoded)
        } catch (e: JWTVerificationException) {
            log.error("stale access token failed signature verification", e)
            throw InvalidJwtTokenException(JWT_CANT_VERIFY)
        } catch (e: JwtProviderException) {
            log.error("could not resolve the signing key for the stale access token", e)
            throw InvalidJwtTokenException(JWT_CANT_VERIFY)
        }
    }

    private fun parseUserId(subject: String): UUID =
        try {
            UUID.fromString(subject)
        } catch (e: IllegalArgumentException) {
            log.error("stale access token subject is not a user id")
            throw InvalidJwtTokenException(JWT_MISSING_USER_ID)
        }
}
