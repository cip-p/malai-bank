package com.malai.copilot

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.copilot.service.CopilotProperties
import com.malai.security.MalaiSecurityConfiguration
import com.malai.security.idempotency.IdempotencyProtectedRoutes
import com.malai.security.idempotency.IdempotencyProtectedRoutes.ProtectedPath
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ComponentScan(basePackages = ["com.malai.copilot"])
@EnableConfigurationProperties(CopilotProperties::class)
@Import(
    MalaiSecurityConfiguration::class,
    MalaiClientConfiguration::class,
)
class CopilotServiceConfiguration {

    @Bean
    fun copilotServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "copilot-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/admin/copilot/explanations"),
        ),
    )

    @Bean
    fun copilotServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "copilot-service",
        pathPrefixes = listOf(
            "/api/v1/admin/copilot",
        ),
    )
}
