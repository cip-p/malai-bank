package com.malai.commons.tracking

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.UUID

@Configuration
@Profile("e2e-tests")
class MdcTraceIdServletFilterConfig {

    @Bean
    fun e2eMdcTraceIdServletFilter(): FilterRegistrationBean<Filter> =
        FilterRegistrationBean(Filter { request, response, chain ->
            if (MDC.get(MDC_TRACE_ID_KEY) != null) {
                chain.doFilter(request, response)
            } else {
                MDC.put(MDC_TRACE_ID_KEY, traceIdFor(request as HttpServletRequest))
                try {
                    chain.doFilter(request, response)
                } finally {
                    MDC.remove(MDC_TRACE_ID_KEY)
                }
            }
        }).apply {
            order = Int.MIN_VALUE + 1
        }

    private fun traceIdFor(request: HttpServletRequest): String =
        request.getHeader(MDC_TRACE_ID_HEADER) ?: UUID.randomUUID().toString().replace("-", "")

    companion object {
        const val MDC_TRACE_ID_HEADER = "X-Test-Trace-Id"
        const val MDC_TRACE_ID_KEY = "trace_id"
    }
}
