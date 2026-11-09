package com.malai.security

import com.malai.auth.support.SpringTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@SpringTest
internal class CorsTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `return CORS headers for GET from localhost`() {
        val httpResponse = getFromOrigin("http://localhost:5173")
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173")
    }

    @Test
    fun `return CORS headers for GET from dev`() {
        val httpResponse = getFromOrigin("https://app.fcd8ayxqr72.com")
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("https://app.fcd8ayxqr72.com")
    }

    @Test
    fun `return CORS headers for GET from prod`() {
        val httpResponse = getFromOrigin("https://app.malaibank.com")
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("https://app.malaibank.com")
    }

    @Test
    fun `not return CORS headers for GET from some other domain`() {
        val httpResponse = getFromOrigin("https://evilhacker.com")
        assertThat(httpResponse.headerNames).doesNotContain("Access-Control-Allow-Origin")
    }

    @Test
    fun `not return CORS headers for GET from prod without https`() {
        val httpResponse = getFromOrigin("http://app.malaibank.com")
        assertThat(httpResponse.headerNames).doesNotContain("Access-Control-Allow-Origin")
    }

    @Test
    fun `return CORS headers for POST from localhost`() {
        val httpResponse = postToOrigin("http://localhost:5173")
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173")
    }

    @Test
    fun `return CORS headers for POST from dev`() {
        val httpResponse = postToOrigin("https://app.fcd8ayxqr72.com")
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("https://app.fcd8ayxqr72.com")
    }

    @Test
    fun `return CORS headers for POST from prod`() {
        val httpResponse = postToOrigin("https://app.malaibank.com")
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("https://app.malaibank.com")
    }

    @Test
    fun `not return CORS headers for POST from some other domain`() {
        val httpResponse = postToOrigin("https://evilhacker.com")
        assertThat(httpResponse.headerNames).doesNotContain("Access-Control-Allow-Origin")
    }

    @Test
    fun `not return CORS headers for POST from prod without https`() {
        val httpResponse = postToOrigin("http://app.malaibank.com")
        assertThat(httpResponse.headerNames).doesNotContain("Access-Control-Allow-Origin")
    }

    @Test
    fun `return CORS headers on 401 from a secured GET with no token`() {
        val httpResponse = securedRequestFromOrigin(
            MockMvcRequestBuilders.get("/test-security/decode-user-id"),
            origin = "http://localhost:5173",
        )

        assertThat(httpResponse.status).isEqualTo(401)
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173")
    }

    @Test
    fun `return CORS headers on 401 from a secured POST with no token`() {
        val httpResponse = securedRequestFromOrigin(
            MockMvcRequestBuilders.post("/test-security/decode-user-id"),
            origin = "http://localhost:5173",
        )

        assertThat(httpResponse.status).isEqualTo(401)
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173")
    }

    @Test
    fun `return CORS headers on 401 from a secured path with an invalid token`() {
        val httpResponse = mvc.perform(
            MockMvcRequestBuilders.get("/test-security/decode-user-id")
                .header("origin", "http://localhost:5173")
                .header("host", "api.app.malaibank.com")
                .header("Authorization", "Bearer not-a-real-jwt")
        )
            .andReturn()
            .response

        assertThat(httpResponse.status).isEqualTo(401)
        assertThat(httpResponse.headerNames).contains("Access-Control-Allow-Origin")
        assertThat(httpResponse.getHeaderValue("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173")
    }

    @Test
    fun `reject secured request from disallowed origin with 403 and no CORS headers`() {
        val httpResponse = securedRequestFromOrigin(
            MockMvcRequestBuilders.get("/test-security/decode-user-id"),
            origin = "https://evilhacker.com",
        )

        assertThat(httpResponse.status).isEqualTo(403)
        assertThat(httpResponse.headerNames).doesNotContain("Access-Control-Allow-Origin")
    }

    private fun securedRequestFromOrigin(
        request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
        origin: String,
    ): MockHttpServletResponse {
        return mvc.perform(
            request
                .header("origin", origin)
                .header("host", "api.app.malaibank.com")
        )
            .andReturn()
            .response
    }

    private fun getFromOrigin(origin: String): MockHttpServletResponse {
        val path = "/test-cors/get"
        val httpResponse = mvc.perform(
            MockMvcRequestBuilders.get(path)
                .contentType(MediaType.TEXT_PLAIN)
                .header("origin", origin)
                .header("host", "api.app.malaibank.com")
        )
            .andReturn()
            .response
        return httpResponse
    }

    private fun postToOrigin(origin: String): MockHttpServletResponse {
        val path = "/test-cors/post"
        val httpResponse = mvc.perform(
            MockMvcRequestBuilders.post(path)
                .contentType(MediaType.TEXT_PLAIN)
                .header("origin", origin)
                .header("host", "api.app.malaibank.com")
        )
            .andReturn()
            .response
        return httpResponse
    }
}
