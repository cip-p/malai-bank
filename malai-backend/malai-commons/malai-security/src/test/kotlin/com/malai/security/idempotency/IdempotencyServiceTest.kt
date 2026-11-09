package com.malai.security.idempotency

import com.malai.commons.TimeService
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.security.MessageDigest

class IdempotencyServiceTest {

    private val repo: IdempotencyKeyRepository = mock(IdempotencyKeyRepository::class.java)
    private val timeService: TimeService = mock(TimeService::class.java)
    private val inserter: IdempotencyKeyInserter = mock(IdempotencyKeyInserter::class.java)
    private val service = IdempotencyService(repo, timeService, inserter)

    @Test
    fun `computeHash with no query string and no body hashes method + uri + empty + empty`() {
        val request = stubRequest(method = "POST", uri = "/api/v1/foo", queryString = null)

        val actual = service.computeHash(request, ByteArray(0))

        assertThat(actual).isEqualTo(sha256("POST\n/api/v1/foo\n\n".toByteArray()))
    }

    @Test
    fun `computeHash treats null and empty query string identically`() {
        val nullQuery = stubRequest("POST", "/api/v1/foo", queryString = null)
        val emptyQuery = stubRequest("POST", "/api/v1/foo", queryString = "")
        val body = """{"x":1}""".toByteArray()

        assertThat(service.computeHash(nullQuery, body))
            .isEqualTo(service.computeHash(emptyQuery, body))
    }

    @Test
    fun `computeHash with a body hashes method + uri + empty + body bytes`() {
        val request = stubRequest("POST", "/api/v1/foo", queryString = null)
        val body = """{"x":1}""".toByteArray()

        val actual = service.computeHash(request, body)

        val expected = sha256("POST\n/api/v1/foo\n\n".toByteArray() + body)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `computeHash with a single query param includes it in the canonical payload`() {
        val request = stubRequest("GET", "/api/v1/foo", queryString = "id=42")

        val actual = service.computeHash(request, ByteArray(0))

        assertThat(actual).isEqualTo(sha256("GET\n/api/v1/foo\nid=42\n".toByteArray()))
    }

    @Test
    fun `computeHash sorts multi-param query strings alphabetically`() {
        val request = stubRequest("GET", "/api/v1/foo", queryString = "z=1&a=2&m=3")

        val actual = service.computeHash(request, ByteArray(0))

        assertThat(actual).isEqualTo(sha256("GET\n/api/v1/foo\na=2&m=3&z=1\n".toByteArray()))
    }

    @Test
    fun `computeHash returns the same hash regardless of query param order`() {
        val ordered = stubRequest("GET", "/api/v1/foo", queryString = "a=1&b=2&c=3")
        val reordered = stubRequest("GET", "/api/v1/foo", queryString = "c=3&a=1&b=2")
        val body = ByteArray(0)

        assertThat(service.computeHash(ordered, body))
            .isEqualTo(service.computeHash(reordered, body))
    }

    @Test
    fun `computeHash drops empty segments from a malformed query string`() {
        val padded = stubRequest("GET", "/api/v1/foo", queryString = "&a=1&")
        val clean = stubRequest("GET", "/api/v1/foo", queryString = "a=1")

        assertThat(service.computeHash(padded, ByteArray(0)))
            .isEqualTo(service.computeHash(clean, ByteArray(0)))
    }

    @Test
    fun `computeHash produces different hashes for different bodies`() {
        val request = stubRequest("POST", "/api/v1/foo", queryString = null)

        val a = service.computeHash(request, """{"x":1}""".toByteArray())
        val b = service.computeHash(request, """{"x":2}""".toByteArray())

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `computeHash produces different hashes for different methods`() {
        val post = stubRequest("POST", "/api/v1/foo", queryString = null)
        val put = stubRequest("PUT", "/api/v1/foo", queryString = null)
        val body = ByteArray(0)

        assertThat(service.computeHash(post, body))
            .isNotEqualTo(service.computeHash(put, body))
    }

    @Test
    fun `computeHash produces different hashes for different URIs`() {
        val a = stubRequest("POST", "/api/v1/foo", queryString = null)
        val b = stubRequest("POST", "/api/v1/bar", queryString = null)
        val body = ByteArray(0)

        assertThat(service.computeHash(a, body))
            .isNotEqualTo(service.computeHash(b, body))
    }

    @Test
    fun `computeHash produces different hashes when the same params have different values`() {
        val a = stubRequest("GET", "/api/v1/foo", queryString = "id=1")
        val b = stubRequest("GET", "/api/v1/foo", queryString = "id=2")

        assertThat(service.computeHash(a, ByteArray(0)))
            .isNotEqualTo(service.computeHash(b, ByteArray(0)))
    }

    @Test
    fun `computeHash output is always 32 bytes long`() {
        val request = stubRequest("POST", "/api/v1/foo", queryString = "a=1&b=2")

        val hash = service.computeHash(request, """{"x":1}""".toByteArray())

        assertThat(hash).hasSize(32)
    }

    private fun stubRequest(method: String, uri: String, queryString: String?): HttpServletRequest {
        val request = mock(HttpServletRequest::class.java)
        given(request.method).willReturn(method)
        given(request.requestURI).willReturn(uri)
        given(request.queryString).willReturn(queryString)
        return request
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)
}
