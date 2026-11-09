package com.malai.commons.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import tools.jackson.databind.JsonNode
import net.logstash.logback.encoder.LogstashEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Acceptance tests for [RequestResponseLoggingFilter].
 *
 * Drives real HTTP through MockMvc with the actual filter wired in, captures
 * the resulting log event via a Logback [ListAppender], encodes it with the
 * production [LogstashEncoder] (the same encoder configured in
 * `logback-spring.xml`), and asserts on the JSON shape.
 *
 * Encoding via the real [LogstashEncoder] rather than poking at the captured
 * event's argument array means the assertions match what we'd actually see in
 * CloudWatch — `kv("request", map)` rendering as a nested JSON object, sensitive
 * fields stringified to `"*****"`, etc.
 *
 * The filter doesn't depend on the rest of the app, so we wire it via
 * [MockMvcBuilders.standaloneSetup] with a small in-test stub controller.
 * That keeps the test fast and self-contained — Spring app boot, databases,
 * external services etc. aren't needed to verify what an audit-log filter
 * does to an HTTP transaction.
 */
class RequestResponseLoggingFilterAcceptanceTest {

    private val objectMapper = jacksonObjectMapper()
    private val filter = RequestResponseLoggingFilter(objectMapper)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(StubController())
        .addFilter<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(filter)
        .build()

    private lateinit var listAppender: ListAppender<ILoggingEvent>
    private lateinit var encoder: LogstashEncoder

    @BeforeEach
    fun setup() {
        val logger = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java) as Logger
        logger.level = Level.INFO
        listAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(listAppender)
        encoder = LogstashEncoder().apply {
            context = logger.loggerContext
            start()
        }
    }

    @AfterEach
    fun tearDown() {
        val logger = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java) as Logger
        logger.detachAppender(listAppender)
        encoder.stop()
    }

    @Test
    fun `logs one entry per transaction with both request and response`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice@example.com","password":"hunter2"}""")
        )

        assertThat(auditEntries()).hasSize(1)
        val entry = lastAuditEntry()
        assertThat(entry.has("request")).isTrue
        assertThat(entry.has("response")).isTrue
        // The formatted message is human-readable in plain-text layouts.
        assertThat(entry.path("message").asString())
            .matches("""http POST /auth/login -> 200 \(\d+ms\)""")
    }

    @Test
    fun `redacts sensitive fields in request and response bodies`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice@example.com","password":"hunter2"}""")
        )

        val entry = lastAuditEntry()
        val reqBody = entry.path("request").path("body")
        assertThat(reqBody.path("username").asString()).isEqualTo("*****")
        assertThat(reqBody.path("password").asString()).isEqualTo("*****")

        val resBody = entry.path("response").path("body")
        assertThat(resBody.path("userId").asString()).isEqualTo("uid-123")
        assertThat(resBody.path("accessToken").asString()).isEqualTo("*****")
        assertThat(resBody.path("refreshToken").asString()).isEqualTo("*****")
        assertThat(resBody.path("idToken").asString()).isEqualTo("*****")
        assertThat(resBody.path("expiresIn").asInt()).isEqualTo(3600)
    }

    @Test
    fun `captures method, path, status and execution time`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice@example.com","password":"hunter2"}""")
        )

        val entry = lastAuditEntry()
        assertThat(entry.path("request").path("method").asString()).isEqualTo("POST")
        assertThat(entry.path("request").path("path").asString()).isEqualTo("/auth/login")
        assertThat(entry.path("response").path("status").asInt()).isEqualTo(200)
        assertThat(entry.path("response").path("executionTimeMillis").isNumber).isTrue
        assertThat(entry.path("response").path("executionTimeMillis").asLong()).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `excludes sensitive headers from request log`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/echo")
                .header("Authorization", "Bearer some-secret-token")
                .header("Cookie", "sessionid=abc123")
                .header("X-API-Key", "k-secret")
                .header("X-Custom", "visible-value")
        )

        val headers = lastAuditEntry().path("request").path("headers")
        assertThat(fieldNamesLower(headers)).doesNotContain(
            "authorization", "cookie", "x-api-key",
        )
        // A non-sensitive custom header still gets logged.
        assertThat(fieldNamesLower(headers)).contains("x-custom")
    }

    @Test
    fun `excludes sensitive headers from response log`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/with-sensitive-response-headers")
        )

        val headers = lastAuditEntry().path("response").path("headers")
        assertThat(fieldNamesLower(headers)).doesNotContain("set-cookie")
    }

    @Test
    fun `skips probe endpoints entirely`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/version")
        )
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/ready")
        )

        assertThat(auditEntries()).isEmpty()
    }

    @Test
    fun `summarises non-json request bodies by content type and size`() {
        val plainText = "this is not json"

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/raw")
                .contentType(MediaType.TEXT_PLAIN)
                .content(plainText)
        )

        val body = lastAuditEntry().path("request").path("body")
        assertThat(body.path("contentType").asString()).startsWith("text/plain")
        assertThat(body.path("size").asInt()).isEqualTo(plainText.toByteArray().size)
        // Make sure we did NOT include the bytes themselves.
        assertThat(body.has("contents")).isFalse
        assertThat(body.toString()).doesNotContain(plainText)
    }

    @Test
    fun `summarises oversize bodies as truncated rather than parsing them`() {
        // 5 KB of valid JSON — bigger than MAX_BODY_BYTES (4 KB).
        val bigPayload = """{"x":"${"a".repeat(5000)}"}"""
        check(bigPayload.toByteArray().size > 4096) { "test fixture must exceed 4 KB" }

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bigPayload)
        )

        val body = lastAuditEntry().path("request").path("body")
        assertThat(body.path("truncated").asBoolean()).isTrue
        assertThat(body.path("size").asInt()).isEqualTo(bigPayload.toByteArray().size)
    }

    @Test
    fun `omits body field when there is no body`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/echo")
        )

        val request = lastAuditEntry().path("request")
        assertThat(request.has("body")).isFalse
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun auditEntries(): List<JsonNode> =
        listAppender.list
            .filter { it.loggerName == RequestResponseLoggingFilter::class.java.name }
            .map { encoder.encode(it) }
            .map { objectMapper.readTree(it) }

    private fun lastAuditEntry(): JsonNode =
        auditEntries().lastOrNull() ?: error("no http_audit log entry was emitted")

    private fun fieldNamesLower(node: JsonNode): Set<String> =
        node.propertyNames().asSequence().map { it.lowercase() }.toSet()

    /**
     * Stub controller covering the request shapes the tests need:
     *  - `/auth/login`         echoes a token-bearing response
     *  - `/echo`               returns `{}` with no request body
     *  - `/raw`                accepts arbitrary text bodies
     *  - `/with-sensitive-response-headers` sets `Set-Cookie`
     *  - `/version` and `/ready` exist purely to prove the filter skips them.
     */
    @RestController
    class StubController {

        @PostMapping("/auth/login")
        fun login(@RequestBody body: Map<String, Any>): Map<String, Any> = mapOf(
            "userId" to "uid-123",
            "accessToken" to "tok-access",
            "refreshToken" to "tok-refresh",
            "idToken" to "tok-id",
            "expiresIn" to 3600,
        )

        @GetMapping("/echo")
        fun echo(): Map<String, Any> = emptyMap()

        @PostMapping("/raw", consumes = [MediaType.TEXT_PLAIN_VALUE])
        fun raw(@RequestBody body: String): Map<String, Any> = mapOf("ok" to true)

        @GetMapping("/with-sensitive-response-headers")
        fun setCookie(response: jakarta.servlet.http.HttpServletResponse): Map<String, Any> {
            response.setHeader("Set-Cookie", "sessionid=secret; HttpOnly")
            response.setHeader("X-Visible", "yes")
            return mapOf("ok" to true)
        }

        @GetMapping("/version") fun version() = "DEV-SNAPSHOT"
        @GetMapping("/ready") fun ready() = "OK"
    }
}
