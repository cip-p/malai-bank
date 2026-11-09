package com.malai.security

import com.malai.auth.support.SpringTest
import com.malai.security.SecurityConfig.Companion.ACCESS_TOKEN_COOKIE
import com.malai.security.SecurityConfig.Companion.ACCOUNT_HOLDER_ID_HEADER
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import com.malai.test.commons.support.WithJwtTokenCognito
import com.malai.test.commons.support.jwt.CognitoJwtTokenSupport
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

@SpringTest
@TestPropertySource(properties = [
    "malai.cognito.jwtToken.issuer=http://localhost:9999/stubbed-cognito-issuer"
])
class SecurityTest : WithJwtTokenCognito {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var cognitoJwtTokenSupport: CognitoJwtTokenSupport

    @Test
    fun `get userId from security Principal`() {
        val userId = UUID.randomUUID()

        mvc.perform(
            get("/test-security/decode-user-id")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${cognitoJwtTokenSupport.generateJwtTokenForUserId(userId)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.accountHolderId").doesNotHaveJsonPath())
    }

    @Test
    fun `get userId and accountHolderId from security Principal`() {
        val userId = UUID.randomUUID()
        val accountHolderId = UUID.randomUUID()

        mvc.perform(
            get("/test-security/decode-user-id-and-account-holder-id")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${cognitoJwtTokenSupport.generateJwtTokenForUserId(userId)}")
                .header(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.accountHolderId").value(accountHolderId.toString()))
    }

    @Test
    fun `not authorize request when the JWT token is missing in request`() {
        mvc.perform(
            get("/test-security/decode-user-id")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("5982"))
    }

    @Test
    fun `forbid request when account holder id header is missing in request`() {
        val userId = UUID.randomUUID()

        mvc.perform(
            get("/test-security/decode-user-id-and-account-holder-id")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${cognitoJwtTokenSupport.generateJwtTokenForUserId(userId)}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("4999"))
    }

    @Test
    fun `authenticate via access_token cookie when no Authorization header is present`() {
        val userId = UUID.randomUUID()

        mvc.perform(
            get("/test-security/decode-user-id")
                .cookie(Cookie(ACCESS_TOKEN_COOKIE, cognitoJwtTokenSupport.generateJwtTokenForUserId(userId)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
    }

    @Test
    fun `cookie wins over Authorization header when both are present`() {
        val cookieUserId = UUID.randomUUID()
        val headerUserId = UUID.randomUUID()

        mvc.perform(
            get("/test-security/decode-user-id")
                .cookie(Cookie(ACCESS_TOKEN_COOKIE, cognitoJwtTokenSupport.generateJwtTokenForUserId(cookieUserId)))
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${cognitoJwtTokenSupport.generateJwtTokenForUserId(headerUserId)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(cookieUserId.toString()))
    }

    @Test
    fun `fall through to Authorization header when access_token cookie is blank`() {
        val userId = UUID.randomUUID()

        mvc.perform(
            get("/test-security/decode-user-id")
                .cookie(Cookie(ACCESS_TOKEN_COOKIE, ""))
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${cognitoJwtTokenSupport.generateJwtTokenForUserId(userId)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
    }
}
