package com.malai.aml

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import com.malai.security.idempotency.IdempotencyProtectedRoutes
import com.malai.security.idempotency.IdempotencyProtectedRoutes.ProtectedPath
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.aml"])
@EnableJpaRepositories(basePackages = ["com.malai.aml"])
@EntityScan(basePackages = ["com.malai.aml"])
@Import(
    MalaiSecurityConfiguration::class,
    MalaiClientConfiguration::class,
)
class AmlServiceConfiguration {

    @Bean
    fun amlServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "aml-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/admin/aml/cases/[^/]+/sar-draft"),
            ProtectedPath("POST", "/api/v1/admin/aml/cases/[^/]+/close-dismissed"),
            ProtectedPath("POST", "/api/v1/admin/aml/cases/[^/]+/close-reported"),
        ),
    )

    @Bean
    fun amlServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "aml-service",
        pathPrefixes = listOf(
            "/api/v1/admin/aml",
        ),
    )
}
