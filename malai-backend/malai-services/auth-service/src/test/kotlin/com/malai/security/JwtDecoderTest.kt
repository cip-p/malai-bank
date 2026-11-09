package com.malai.security

import com.malai.auth.support.SpringTest
import com.malai.commons.exception.InvalidJwtTokenException
import com.malai.test.commons.support.WithJwtTokenCognito
import com.malai.test.commons.support.jwt.JwtTokenBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.util.*

@SpringTest
@TestPropertySource(properties = [
    "malai.cognito.jwtToken.issuer=http://localhost:9999/stubbed-cognito-issuer"
])
class JwtDecoderTest : WithJwtTokenCognito {

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `generate JWT token which will be used in manual testing`() {
        val userId = UUID.randomUUID()
        val token = generateJwtTokenForUserId(userId)

        println(userId)
        println(token)
    }

    @Test
    fun `parse and decode JWT token for userId only`() {
        val userId = UUID.randomUUID()
        val token = generateJwtTokenForUserId(userId)

        val decodedToken = jwtDecoder.decodeAndVerify(token)

        assertThat(decodedToken.token).isEqualTo(token)
        assertThat(decodedToken.subject).isEqualTo(userId.toString())
        assertThat(decodedToken.expiresAt).isAfter(Date())
        assertThat(decodedToken.issuer).isEqualTo("http://localhost:9999/stubbed-cognito-issuer")
        assertThat(decodedToken.getClaim("account_holder_id").isMissing).isTrue
        assertThat(decodedToken.getClaim("token_use").asString()).isEqualTo("access")

        assertThat(decodedToken.getHeaderClaim("kid").asString()).isEqualTo(getKeyId())
        assertThat(decodedToken.getHeaderClaim("typ").asString()).isEqualTo("JWT")
        assertThat(decodedToken.getHeaderClaim("alg").asString()).isEqualTo("RS256")
    }

    @Test
    fun `throw exception when userId is missing from the JWT token`() {
        val token = generateJwtToken(
            JwtTokenBuilder.userJwtTokenBuilderWithDefaultValues()
                .withUserId(null)
        )

        assertThatThrownBy { jwtDecoder.decodeAndVerify(token) }
            .isInstanceOf(InvalidJwtTokenException::class.java)
            .hasMessage("5987")
    }

    @Test
    fun `throw exception when expiresAt is in past in the JWT token`() {
        val token = generateJwtToken(
            JwtTokenBuilder.userJwtTokenBuilderWithDefaultValues()
                .withTtlMinutes(-1)
        )

        assertThatThrownBy { jwtDecoder.decodeAndVerify(token) }
            .isInstanceOf(InvalidJwtTokenException::class.java)
            .hasMessage("5986")
    }

    @Test
    fun `throw exception when JWT token type is missing from the JWT token`() {
        val token = generateJwtToken(
            JwtTokenBuilder.userJwtTokenBuilderWithDefaultValues()
                .withType(null)
        )

        assertThatThrownBy { jwtDecoder.decodeAndVerify(token) }
            .isInstanceOf(InvalidJwtTokenException::class.java)
            .hasMessage("5985")
    }

    @Test
    fun `throw exception when JWT token type is not id`() {
        val token = generateJwtToken(
            JwtTokenBuilder.userJwtTokenBuilderWithDefaultValues()
                .withType("refresh")
        )

        assertThatThrownBy { jwtDecoder.decodeAndVerify(token) }
            .isInstanceOf(InvalidJwtTokenException::class.java)
            .hasMessage("5985")
    }

    @Test
    fun `cache the cognito public key and not fail on subsequent requests when cognito is down`() {
        val userId = UUID.randomUUID()
        val token = generateJwtTokenForUserId(userId)
        val firstDecodedToken = jwtDecoder.decodeAndVerify(token)

        cognitoStub().stop()

        val secondDecodedToken = jwtDecoder.decodeAndVerify(token)
        assertThat(secondDecodedToken.token).isEqualTo(firstDecodedToken.token)
    }
}
