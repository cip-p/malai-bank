package com.malai.commons.tracking

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Installs a servlet filter that maps the inbound URL to a logical malai-service via the
 * [MalaiSourceRoutes] beans contributed by each service module, and populates
 * [MalaiSourceContext] for the duration of the request. Downstream outbound calls
 * (AbstractMalaiClient HTTP, MalaiSourceAwsSdkInterceptorConfig AWS SDK) then tag
 * `X-Malai-Source` with the logical service rather than the deployment unit.
 *
 * Always-on (no `@Profile` gate): the same source resolution applies to production logs and
 * traces, not only to acceptance / e2e tests.
 */
@Configuration
class MalaiSourceServletFilterConfig(
    private val routes: List<MalaiSourceRoutes>,
) {

    @Bean
    fun malaiSourcePropagationFilter(): FilterRegistrationBean<Filter> {
        val prefixToName = routes
            .flatMap { route -> route.pathPrefixes.map { it to route.serviceName } }
            .sortedByDescending { it.first.length }
        return FilterRegistrationBean(Filter { request, response, chain ->
            val uri = (request as HttpServletRequest).requestURI
            val source = prefixToName.firstOrNull { uri.startsWith(it.first) }?.second
            if (source != null) {
                MalaiSourceContext.value = MalaiSource(source)
                try {
                    chain.doFilter(request, response)
                } finally {
                    MalaiSourceContext.unset()
                }
            } else {
                chain.doFilter(request, response)
            }
        }).apply {
            order = Int.MIN_VALUE + 1
        }
    }
}
