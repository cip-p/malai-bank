package com.malai.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.malai.commons.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit.HOURS
import java.util.Date
import java.util.UUID

internal class StaleTokenIdentityReaderTest {

    private val signingKeyPair = generateRsaKeyPair()
    private val otherKeyPair = generateRsaKeyPair()

    private val reader = StaleTokenIdentityReader(keyProviderReturning(signingKeyPair.public as RSAPublicKey))

    @Test
    fun `reads the subject from a token whose signature is valid but whose expiry has passed`() {
        val userId = UUID.randomUUID()
        val expiredToken = signedToken(userId.toString(), signingKeyPair, expiresAt = anHourAgo())

        assertThat(reader.readSubject(expiredToken)).isEqualTo(userId)
    }

    @Test
    fun `rejects a token whose signature does not verify against the JWKS`() {
        val wronglySignedToken = signedToken(UUID.randomUUID().toString(), otherKeyPair, expiresAt = anHourAgo())

        assertThatThrownBy { reader.readSubject(wronglySignedToken) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `rejects a malformed token`() {
        assertThatThrownBy { reader.readSubject("not-a-jwt") }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `rejects a token whose subject is not a user id`() {
        val tokenWithNonUuidSubject = signedToken("not-a-uuid", signingKeyPair, expiresAt = anHourAgo())

        assertThatThrownBy { reader.readSubject(tokenWithNonUuidSubject) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    private fun signedToken(subject: String, keyPair: KeyPair, expiresAt: Instant): String =
        JWT.create()
            .withKeyId("test-kid")
            .withSubject(subject)
            .withClaim("token_use", "access")
            .withExpiresAt(Date.from(expiresAt))
            .sign(Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey))

    private fun anHourAgo(): Instant = Instant.now().minus(1, HOURS)

    private fun keyProviderReturning(publicKey: RSAPublicKey) = object : RSAKeyProvider {
        override fun getPublicKeyById(keyId: String?): RSAPublicKey = publicKey
        override fun getPrivateKey(): RSAPrivateKey = throw UnsupportedOperationException()
        override fun getPrivateKeyId(): String = throw UnsupportedOperationException()
    }

    private fun generateRsaKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
}
