package com.malai.standingorder

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
@ComponentScan(basePackages = ["com.malai.standingorder"])
@EnableJpaRepositories(basePackages = ["com.malai.standingorder"])
@EntityScan(basePackages = ["com.malai.standingorder"])
@Import(MalaiSecurityConfiguration::class, MalaiClientConfiguration::class)
class StandingOrderServiceConfiguration {

    @Bean
    fun standingOrderServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "standing-order-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/standing-orders"),
        ),
    )

    @Bean
    fun standingOrderServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "standing-order-service",
        pathPrefixes = listOf("/api/v1/standing-orders"),
    )
}
