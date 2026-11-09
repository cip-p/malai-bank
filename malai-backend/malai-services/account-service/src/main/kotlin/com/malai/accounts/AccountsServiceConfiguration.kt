package com.malai.accounts

import com.malai.accounts.identifier.BankIdentifierProperties
import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import com.malai.security.idempotency.IdempotencyProtectedRoutes
import com.malai.security.idempotency.IdempotencyProtectedRoutes.ProtectedPath
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.accounts"])
@EnableJpaRepositories(basePackages = ["com.malai.accounts"])
@EntityScan(basePackages = ["com.malai.accounts"])
@EnableConfigurationProperties(BankIdentifierProperties::class)
@Import(
    MalaiSecurityConfiguration::class,
    MalaiClientConfiguration::class,
)
class AccountsServiceConfiguration {

    @Bean
    fun accountServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "account-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/accounts/[^/]+/transfers"),
            ProtectedPath("POST", "/api/v1/confirmation-of-payee"),
            ProtectedPath("POST", "/api/v1/credit-card-applications/[^/]+"),
        ),
    )

    @Bean
    fun accountServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "account-service",
        pathPrefixes = listOf(
            "/api/v1/accounts",
            "/api/v1/confirmation-of-payee",
            "/api/v1/credit-card-applications",
            "/api/v1/internal/accounts",
            "/api/v1/internal/onboarding/accounts",
        ),
    )
}
