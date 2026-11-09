package com.malai.test.acceptance

import com.malai.test.commons.MalaiServiceTestConfiguration
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile

/**
 * Registers the test-only bean overrides (notably [com.malai.test.commons.support.StubbedTimeService]
 * and [com.malai.test.commons.TestUuidGenerator]) when a malai service is booted via its
 * `runXxxService(...)` helper for acceptance tests.
 *
 * Because this class lives outside each service's `@SpringBootApplication` component-scan path,
 * every `runXxxService(...)` must register it explicitly as an additional primary source on the
 * `SpringApplication`, e.g.:
 *
 * ```
 * SpringApplication(FooServiceApplication::class.java, AcceptanceTestsConfiguration::class.java)
 *     .run(...)
 * ```
 */
@Configuration
@Profile("acceptance-tests")
@Import(MalaiServiceTestConfiguration::class)
class AcceptanceTestsConfiguration {

    // Test-scope simulation of the OTel Java agent's logback-mdc instrumentation
    // (Plan 02-16 / BACK-10). The agent's instrumentation puts `trace_id` /
    // `span_id` into MDC on the request handler thread in production; the agent
    // isn't loaded in acceptance tests. Acceptance tests that need to assert the
    // trace_id end-to-end correlation (header -> audit_events.trace_id ==
    // ledger_entries.trace_id) send `X-Test-Trace-Id` and this filter mirrors
    // it onto `MDC.trace_id` for the request lifetime.
    @Bean
    fun mdcTraceIdServletFilter(): FilterRegistrationBean<Filter> =
        FilterRegistrationBean(Filter { request, response, chain ->
            val incoming = (request as HttpServletRequest).getHeader(MDC_TRACE_ID_HEADER)
            if (incoming != null) {
                MDC.put(MDC_TRACE_ID_KEY, incoming)
                try {
                    chain.doFilter(request, response)
                } finally {
                    MDC.remove(MDC_TRACE_ID_KEY)
                }
            } else {
                chain.doFilter(request, response)
            }
        }).apply {
            // Run before the JwtDecoderFilter / audit-emitting service code so
            // MDC.trace_id is visible everywhere downstream.
            order = Int.MIN_VALUE + 1
        }

    companion object {
        const val MDC_TRACE_ID_HEADER = "X-Test-Trace-Id"
        const val MDC_TRACE_ID_KEY = "trace_id"
    }
}
