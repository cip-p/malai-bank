package com.malai.security

import com.malai.auth.support.SpringTest
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import com.malai.test.commons.support.WithJwtTokenCognito
import com.malai.test.commons.support.jwt.CognitoJwtTokenSupport
import com.malai.test.commons.support.jwt.JwtTokenBuilder.Companion.userJwtTokenBuilderWithDefaultValues
import com.malai.test.commons.support.jwt.JwtTokenBuilder.Companion.serviceJwtTokenBuilderWithDefaultValues
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Covers the service-to-service filter chain mounted on the internal-services path prefix. The
 * filter under test ([ServiceJwtFilter]) reuses [JwtDecoder] for signature/issuer/exp validation
 * and adds a scope check. Tests use [TestInternalServiceController] so we exercise the real
 * filter chain end-to-end and assert on the resulting [ServicePrincipal].
 */
@SpringTest
@TestPropertySource(properties = [
    "malai.cognito.jwtToken.issuer=http://localhost:9999/stubbed-cognito-issuer"
])
class ServiceJwtFilterTest : WithJwtTokenCognito {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var cognitoJwtTokenSupport: CognitoJwtTokenSupport

    @Test
    fun `accept valid service JWT and populate ServicePrincipal`() {
        val token = cognitoJwtTokenSupport.generateJwtToken(
            serviceJwtTokenBuilderWithDefaultValues()
                .withClientId("registered-client-id")
                .withScopes(listOf("malai-internal/onboarding.write"))
        )

        mvc.perform(
            get("/api/v1/internal/test-security/decode-service-principal")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clientId").value("registered-client-id"))
            .andExpect(jsonPath("$.scopes[0]").value("malai-internal/onboarding.write"))
    }

    @Test
    fun `reject internal request with no Authorization header`() {
        mvc.perform(
            get("/api/v1/internal/test-security/decode-service-principal")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("5982"))
    }

    @Test
    fun `reject internal request with a user JWT (no client_id claim)`() {
        // A regular user-pool access token has `sub` set to a userId and no `client_id` claim.
        val userToken = cognitoJwtTokenSupport.generateJwtTokenForUserId(UUID.randomUUID())

        mvc.perform(
            get("/api/v1/internal/test-security/decode-service-principal")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$userToken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("5987"))
    }

    @Test
    fun `reject internal request when the required scope is not granted`() {
        val token = cognitoJwtTokenSupport.generateJwtToken(
            serviceJwtTokenBuilderWithDefaultValues()
                .withClientId("registered-client-id")
                .withScopes(listOf("malai-internal/some-other-scope"))
        )

        mvc.perform(
            get("/api/v1/internal/test-security/decode-service-principal")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$token")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("5983"))
    }

    @Test
    fun `reject internal request when JWT is not an access token`() {
        val token = cognitoJwtTokenSupport.generateJwtToken(
            serviceJwtTokenBuilderWithDefaultValues()
                .withType("id")
        )

        mvc.perform(
            get("/api/v1/internal/test-security/decode-service-principal")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$token")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("5985"))
    }

    @Test
    fun `reject internal request when JWT is expired`() {
        val token = cognitoJwtTokenSupport.generateJwtToken(
            serviceJwtTokenBuilderWithDefaultValues()
                .withTtlMinutes(-5)
        )

        mvc.perform(
            get("/api/v1/internal/test-security/decode-service-principal")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$token")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("5986"))
    }

    @Test
    fun `internal filter chain takes precedence over the user-JWT chain for internal paths`() {
        // Sanity check: a request to the internal path prefix with a user JWT would have been accepted by
        // the user-JWT chain — verifying the @Order(1) internal chain wins and rejects it on scope grounds.
        val userToken = cognitoJwtTokenSupport.generateJwtToken(
            userJwtTokenBuilderWithDefaultValues().withUserId(UUID.randomUUID())
        )

        mvc.perform(
            get("/api/v1/internal/test-security/decode-service-principal")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$userToken")
        )
            .andExpect(status().isUnauthorized)
    }
}
