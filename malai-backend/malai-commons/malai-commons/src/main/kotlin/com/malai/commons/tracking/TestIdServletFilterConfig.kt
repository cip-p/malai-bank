package com.malai.commons.tracking

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Installs a servlet filter that extracts the [TestId.HEADER] from inbound HTTP requests and
 * populates [TestIdContext] for the duration of the request. Downstream code (HTTP clients,
 * AWS SDK clients) can then propagate the TestId on outbound calls so Kensa can correlate
 * interactions across services in acceptance tests.
 *
 * Activated by the `acceptance-tests` Spring profile.
 */
@Configuration
@Profile("acceptance-tests", "e2e-tests")
class TestIdServletFilterConfig {

    @Bean
    fun testIdPropagationFilter(): FilterRegistrationBean<Filter> =
        FilterRegistrationBean(Filter { request, response, chain ->
            val incoming = (request as HttpServletRequest).getHeader(TestId.HEADER)
            if (incoming != null) {
                TestIdContext.value = TestId(incoming)
                try {
                    chain.doFilter(request, response)
                } finally {
                    TestIdContext.unset()
                }
            } else {
                chain.doFilter(request, response)
            }
        }).apply {
            order = Int.MIN_VALUE // first, so downstream filters/handlers see the context
        }
}
