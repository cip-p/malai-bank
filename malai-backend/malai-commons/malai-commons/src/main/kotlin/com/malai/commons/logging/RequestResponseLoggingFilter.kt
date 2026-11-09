package com.malai.commons.logging

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * Audit logger for every HTTP request/response pair.
 *
 * Emits a single structured JSON log entry per HTTP transaction, with the
 * request and response side-by-side under top-level `request` / `response`
 * keys. The malai logback config uses the Logstash encoder with `<mdc/>`
 * enabled, so every entry inherits the OTel agent's `trace_id` / `span_id`
 * for free, and audit entries cross-link to traces in X-Ray.
 *
 * Example shape (formatted for readability):
 * ```
 * {
 *   "timestamp":"...","level":"INFO","logger":"...",
 *   "message":"http POST /api/v1/auth/login -> 200 (182ms)",
 *   "trace_id":"...","span_id":"...",
 *   "request": {
 *     "method":"POST","path":"/api/v1/auth/login",
 *     "headers":{"Content-Type":"application/json","User-Agent":"..."},
 *     "body":{"username":"*****","password":"*****"}
 *   },
 *   "response": {
 *     "status":200,"executionTimeMillis":182,
 *     "headers":{"Content-Type":"application/json"},
 *     "body":{"userId":"...","accessToken":"*****","refreshToken":"*****",
 *             "idToken":"*****","expiresIn":3600}
 *   }
 * }
 * ```
 *
 * Redaction strategy:
 *   - Body fields by key name (see [BodyRedactor]). Recursive at any depth.
 *   - Sensitive headers (`Authorization`, `Cookie`, ...) are *excluded*
 *     entirely — masked values would still leak length / scheme / presence.
 *   - Non-JSON bodies are summarised as `{contentType, size}` to avoid
 *     leaking binary blobs / multipart uploads.
 *
 * Limitations / caveats:
 *   - Probes (`/ready`, `/version`) are skipped to keep the log stream clean.
 *   - Server-Sent Events (`Accept: text/event-stream`) are skipped because
 *     [ContentCachingResponseWrapper] buffers the whole body, defeating
 *     streaming. None today, but the opt-out keeps future SSE endpoints safe.
 *   - Request body capture relies on something downstream actually reading
 *     the input stream. Requests rejected at the security-filter layer (e.g.
 *     401 on a JWT-protected endpoint before deserialisation) will log
 *     everything except the request body — which is fine, because the body
 *     of a rejected request is exactly the kind of thing we don't want to
 *     keep around.
 *   - Bodies over [MAX_BODY_BYTES] are summarised, not parsed — truncated
 *     JSON usually doesn't reparse cleanly anyway.
 *
 * Wiring: registered as a [org.springframework.boot.web.servlet.FilterRegistrationBean]
 * by [RequestResponseLoggingConfiguration] at `Ordered.HIGHEST_PRECEDENCE`,
 * so it sits before Spring Security and captures pre-auth requests. The OTel
 * agent instruments Tomcat at the bytecode level (before any servlet filter),
 * so MDC trace context is already populated by the time we run.
 *
 * Lives in `malai-commons` so every Spring web service in the malai backend
 * picks it up automatically via the `@ComponentScan` on
 * [com.malai.commons.MalaiCommonsConfiguration].
 */
class RequestResponseLoggingFilter(
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.requestURI in SKIPPED_PATHS) return true
        if (request.method == "OPTIONS") return true

        val accept = request.getHeader("Accept") ?: ""
        return accept.contains("text/event-stream", ignoreCase = true)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val cachedRequest = ContentCachingRequestWrapper(request, Int.MAX_VALUE)
        val cachedResponse = ContentCachingResponseWrapper(response)
        val started = System.currentTimeMillis()

        try {
            filterChain.doFilter(cachedRequest, cachedResponse)
        } finally {
            val durationMs = System.currentTimeMillis() - started
            try {
                emit(cachedRequest, cachedResponse, durationMs)
            } catch (e: Throwable) {
                // The audit log must never fail a request. Swallow + record.
                log.warn("audit log emission failed", e)
            }
            // CRITICAL: flush the cached response body back to the actual
            // output stream. Without this, every response body is silently
            // swallowed by the wrapper.
            cachedResponse.copyBodyToResponse()
        }
    }

    private fun emit(
        req: ContentCachingRequestWrapper,
        res: ContentCachingResponseWrapper,
        durationMs: Long,
    ) {
        val requestEntry = buildMap<String, Any> {
            put("method", req.method)
            put("path", req.requestURI)
            put("headers", extractRequestHeaders(req))
            bodyForLog(req.contentAsByteArray, req.contentType)?.let { put("body", it) }
        }

        val responseEntry = buildMap<String, Any> {
            put("status", res.status)
            put("executionTimeMillis", durationMs)
            put("headers", extractResponseHeaders(res))
            bodyForLog(res.contentAsByteArray, res.contentType)?.let { put("body", it) }
        }

        // Two-shape message: in plain-text layouts (Spring Boot console used by
        // the acceptance tests) the {} placeholders render a single readable
        // summary line. The trailing structured arguments are still picked up
        // by the LogstashEncoder (used in dev/prod) and emitted as nested
        // `request` / `response` JSON objects for full detail.
        log.info(
            "http {} {} -> {} ({}ms)",
            req.method, req.requestURI, res.status, durationMs,
            kv("request", requestEntry),
            kv("response", responseEntry),
        )
    }

    private fun extractRequestHeaders(req: HttpServletRequest): Map<String, String> =
        req.headerNames.toList()
            .filter { it.lowercase() !in EXCLUDED_HEADERS }
            .associateWith { req.getHeader(it) }

    private fun extractResponseHeaders(res: HttpServletResponse): Map<String, String> =
        res.headerNames
            .filter { it.lowercase() !in EXCLUDED_HEADERS }
            .associateWith { res.getHeader(it) }

    /**
     * Returns the body to log:
     *  - `null` for empty bodies (caller omits the field).
     *  - For JSON bodies under [MAX_BODY_BYTES]: parsed + recursively redacted.
     *  - For non-JSON, oversize, or unparseable: a summary `{contentType, size}`
     *    (with `truncated` / `parseError` flags as appropriate).
     */
    private fun bodyForLog(bytes: ByteArray, contentType: String?): Any? {
        if (bytes.isEmpty()) return null

        if (bytes.size > MAX_BODY_BYTES) {
            return mapOf(
                "contentType" to (contentType ?: "unknown"),
                "size" to bytes.size,
                "truncated" to true,
            )
        }

        if (contentType == null || !isJson(contentType)) {
            return mapOf(
                "contentType" to (contentType ?: "unknown"),
                "size" to bytes.size,
            )
        }

        return try {
            val node: JsonNode = objectMapper.readTree(bytes)
            BodyRedactor.redact(node)
        } catch (e: Throwable) {
            mapOf(
                "contentType" to contentType,
                "size" to bytes.size,
                "parseError" to (e.message ?: e.javaClass.simpleName),
            )
        }
    }

    private fun isJson(contentType: String): Boolean {
        val lower = contentType.lowercase()
        return lower.startsWith("application/json") || lower.contains("+json")
    }

    companion object {
        private val log = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java)

        // Header names dropped from audit output. Even masking would leak
        // length / scheme / presence info on bearer tokens & cookies, so we
        // exclude rather than mask. Matched case-insensitively.
        internal val EXCLUDED_HEADERS = setOf(
            "authorization", "cookie", "set-cookie", "x-api-key", "proxy-authorization",
        )

        // Probe paths that fire often enough to dominate the log stream.
        // ALB health-checks /ready every 15s; the smoke test polls /version.
        internal val SKIPPED_PATHS = setOf("/ready", "/version")

        // Cap on either request or response body size for log inclusion.
        // 4 KB comfortably covers typical API payloads; anything larger gets
        // summarised by size only.
        internal const val MAX_BODY_BYTES = 4096
    }
}
