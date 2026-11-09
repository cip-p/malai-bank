package com.malai.commons.logging

import tools.jackson.databind.ObjectMapper
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Registers [RequestResponseLoggingFilter] as a Servlet filter at
 * [Ordered.HIGHEST_PRECEDENCE], so it sits ahead of Spring Security and
 * captures every request — including pre-auth ones (failed logins, missing
 * JWT, etc).
 *
 * Picked up automatically by every malai service via the `@ComponentScan` on
 * [com.malai.commons.MalaiCommonsConfiguration]. Services don't need to
 * declare anything themselves.
 *
 * The wrapping is needed because Spring Boot's auto filter-registration
 * defaults a plain `Filter` `@Bean` to `LOWEST_PRECEDENCE` regardless of any
 * `@Order` annotation. Constructing the registration explicitly is the
 * documented way to control filter ordering.
 */
@Configuration
class RequestResponseLoggingConfiguration {

    @Bean
    fun requestResponseLoggingFilter(
        objectMapper: ObjectMapper,
    ): FilterRegistrationBean<RequestResponseLoggingFilter> {
        val registration = FilterRegistrationBean(RequestResponseLoggingFilter(objectMapper))
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
