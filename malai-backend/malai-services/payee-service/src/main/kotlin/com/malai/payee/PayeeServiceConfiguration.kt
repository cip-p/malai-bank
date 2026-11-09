package com.malai.payee

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
@ComponentScan(basePackages = ["com.malai.payee"])
@EnableJpaRepositories(basePackages = ["com.malai.payee"])
@EntityScan(basePackages = ["com.malai.payee"])
@Import(MalaiSecurityConfiguration::class)
class PayeeServiceConfiguration {

    @Bean
    fun payeeServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "payee-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/payees"),
        ),
    )

    @Bean
    fun payeeServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "payee-service",
        pathPrefixes = listOf("/api/v1/payees"),
    )
}
