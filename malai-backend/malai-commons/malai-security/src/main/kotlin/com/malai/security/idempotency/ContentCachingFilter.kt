package com.malai.security.idempotency

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.web.util.ContentCachingResponseWrapper

class ContentCachingFilter(
    private val matchers: List<IdempotencyInterceptor.PathMethodMatcher>,
    private val maxBodyBytes: Int,
) : Filter {

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val httpReq = req as HttpServletRequest
        val httpRes = res as HttpServletResponse
        if (!shouldWrap(httpReq)) {
            chain.doFilter(req, res)
            return
        }
        val request = CachedBodyHttpServletRequest(httpReq, maxBodyBytes)
        val response = ContentCachingResponseWrapper(httpRes)
        try {
            chain.doFilter(request, response)
        } finally {
            response.copyBodyToResponse()
        }
    }

    private fun shouldWrap(request: HttpServletRequest): Boolean {
        val method = request.method
        if (method != HttpMethod.POST.name() && method != HttpMethod.PUT.name()) return false
        return matchers.any { it.matches(method, request.requestURI) }
    }
}
