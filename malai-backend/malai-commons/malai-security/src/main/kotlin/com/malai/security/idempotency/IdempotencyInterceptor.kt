package com.malai.security.idempotency

import tools.jackson.databind.ObjectMapper
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.IDEMPOTENCY_KEY_INVALID
import com.malai.commons.exception.MalaiCode.IDEMPOTENCY_KEY_IN_FLIGHT
import com.malai.commons.exception.MalaiCode.IDEMPOTENCY_KEY_MISSING
import com.malai.commons.exception.MalaiCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH
import com.malai.commons.exception.UnprocessableEntityException
import com.malai.security.idempotency.IdempotencyStatus.COMPLETED
import com.malai.security.idempotency.IdempotencyStatus.IN_FLIGHT
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.util.ContentCachingResponseWrapper
import org.springframework.web.util.WebUtils

class IdempotencyInterceptor(
    private val serviceName: String,
    private val idempotencyService: IdempotencyService,
    private val matchers: List<PathMethodMatcher>,
    private val maxBodyBytes: Int,
    private val ttlHours: Long,
    private val mapper: ObjectMapper,
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val keyAttr = "$KEY_ATTR_PREFIX.$serviceName"

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (!isProtected(request)) return true

        val key = validatedKey(request.getHeader(IDEMPOTENCY_KEY_HEADER))

        val cachedRequest = WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest::class.java)
        val body = cachedRequest?.cachedBody ?: ByteArray(0)
        val computedHash = idempotencyService.computeHash(request, body)

        val existing = idempotencyService.tryReserve(serviceName, key, computedHash, ttlHours)
        if (existing == null) {
            request.setAttribute(keyAttr, key)
            return true
        }

        return when (existing.status) {
            IN_FLIGHT -> throw ConflictErrorException(IDEMPOTENCY_KEY_IN_FLIGHT)
            COMPLETED -> {
                if (!existing.requestHash.contentEquals(computedHash)) {
                    throw UnprocessableEntityException(IDEMPOTENCY_KEY_PAYLOAD_MISMATCH)
                }
                replayCached(existing, response)
                false
            }
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val key = request.getAttribute(keyAttr) as? String ?: return

        if (ex != null || response.status >= 500) {
            idempotencyService.release(serviceName, key)
            return
        }

        val wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper::class.java)
        if (wrapper == null) {
            idempotencyService.release(serviceName, key)
            return
        }

        val body = wrapper.contentAsByteArray
        if (body.size > maxBodyBytes) {
            log.info(
                "Idempotency: response body {} bytes exceeds cap {} — completing with empty body for service={} key={}",
                body.size, maxBodyBytes, serviceName, key,
            )
            idempotencyService.complete(serviceName, key, wrapper.status, null, ByteArray(0))
            return
        }

        val headers: Map<String, String> = wrapper.headerNames.associateWith { wrapper.getHeader(it) ?: "" }
        val headersJson = mapper.writeValueAsString(headers)

        idempotencyService.complete(
            service = serviceName,
            key = key,
            responseStatus = wrapper.status,
            responseHeadersJson = headersJson,
            responseBody = body,
        )
    }

    private fun replayCached(cached: IdempotencyKeyEntity, response: HttpServletResponse) {
        val target = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper::class.java) ?: response
        cached.responseStatus?.let { target.status = it }
        cached.responseHeaders?.let { headersJson ->
            @Suppress("UNCHECKED_CAST")
            (mapper.readValue(headersJson, Map::class.java) as Map<String, Any?>).forEach { (n, v) ->
                if (v != null) target.setHeader(n, v.toString())
            }
        }
        cached.responseBody?.let { target.outputStream.write(it) }
        target.outputStream.flush()
    }

    private fun validatedKey(raw: String?): String {
        val key = raw ?: throw BadRequestException(IDEMPOTENCY_KEY_MISSING)
        if (key.length !in 1..MAX_KEY_LENGTH || !key.all { it.code in 0x20..0x7E }) {
            throw BadRequestException(IDEMPOTENCY_KEY_INVALID)
        }
        return key
    }

    private fun isProtected(request: HttpServletRequest): Boolean {
        val method = request.method
        if (method != HttpMethod.POST.name() && method != HttpMethod.PUT.name()) return false
        return matchers.any { it.matches(method, request.requestURI) }
    }

    data class PathMethodMatcher(val method: String, val pathRegex: String) {
        private val regex = Regex(pathRegex)
        fun matches(reqMethod: String, path: String): Boolean =
            reqMethod.equals(method, ignoreCase = true) && regex.matches(path)
    }

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val MAX_KEY_LENGTH = 255
        private const val KEY_ATTR_PREFIX = "malai.idempotency.key"
    }
}
