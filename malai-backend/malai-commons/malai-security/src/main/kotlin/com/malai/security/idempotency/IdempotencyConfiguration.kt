package com.malai.security.idempotency

import tools.jackson.databind.ObjectMapper
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdempotencyProperties::class)
@ComponentScan(basePackages = ["com.malai.security.idempotency"])
@EnableJpaRepositories(basePackages = ["com.malai.security.idempotency"])
@EntityScan(basePackages = ["com.malai.security.idempotency"])
@Import(IdempotencyWebMvcConfigurer::class)
class IdempotencyConfiguration

@ConfigurationProperties(prefix = "malai.idempotency")
data class IdempotencyProperties(
    val maxBodyBytes: Int = 1 shl 20, // 1 MiB
    val ttlHours: Long = 24,
)

@Configuration
class IdempotencyWebMvcConfigurer(
    private val protectedRoutes: List<IdempotencyProtectedRoutes>,
    private val service: IdempotencyService,
    private val props: IdempotencyProperties,
    private val mapper: ObjectMapper,
) : WebMvcConfigurer {

    private fun matchersFor(routes: IdempotencyProtectedRoutes): List<IdempotencyInterceptor.PathMethodMatcher> =
        routes.paths.map { IdempotencyInterceptor.PathMethodMatcher(it.method, it.pathRegex) }

    private fun allMatchers(): List<IdempotencyInterceptor.PathMethodMatcher> =
        protectedRoutes.flatMap { matchersFor(it) }

    override fun addInterceptors(registry: InterceptorRegistry) {
        protectedRoutes.forEach { routes ->
            registry
                .addInterceptor(
                    IdempotencyInterceptor(
                        serviceName = routes.serviceName,
                        idempotencyService = service,
                        matchers = matchersFor(routes),
                        maxBodyBytes = props.maxBodyBytes,
                        ttlHours = props.ttlHours,
                        mapper = mapper,
                    )
                )
                .addPathPatterns("/api/**")
        }
    }

    @Bean
    fun contentCachingFilter(): FilterRegistrationBean<ContentCachingFilter> =
        FilterRegistrationBean(ContentCachingFilter(allMatchers(), props.maxBodyBytes)).apply {
            addUrlPatterns("/api/*")
            order = Int.MIN_VALUE
        }
}
