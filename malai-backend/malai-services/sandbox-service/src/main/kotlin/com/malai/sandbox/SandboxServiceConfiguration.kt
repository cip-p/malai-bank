package com.malai.sandbox

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import com.malai.security.idempotency.IdempotencyProtectedRoutes
import com.malai.security.idempotency.IdempotencyProtectedRoutes.ProtectedPath
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ComponentScan(basePackages = [
    "com.malai.sandbox",
    "com.malai.client.malai.stub",
    "com.malai.client.malai.accounts.lookup",
    "com.malai.client.malai.card",
    "com.malai.client.malai.directdebit",
])
@Import(
    MalaiSecurityConfiguration::class,
    MalaiClientConfiguration::class,
)
class SandboxServiceConfiguration {

    @Bean
    fun sandboxServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "sandbox-service",
        pathPrefixes = listOf("/api/v1/sandbox"),
    )

    @Bean
    fun sandboxServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "sandbox-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/sandbox/inbound-payments"),
        ),
    )
}
