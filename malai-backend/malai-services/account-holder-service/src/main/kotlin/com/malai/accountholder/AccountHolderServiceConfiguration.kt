package com.malai.accountholder

import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.accountholder"])
@EnableJpaRepositories(basePackages = ["com.malai.accountholder"])
@EntityScan(basePackages = ["com.malai.accountholder"])
@Import(MalaiSecurityConfiguration::class)
class AccountHolderServiceConfiguration {

    @Bean
    fun accountHolderServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "account-holder-service",
        pathPrefixes = listOf(
            "/api/v1/account-holder",
            "/api/v1/addresses",
            "/api/v1/settle-up",
            "/api/v1/internal/onboarding/account-holders",
            "/api/v1/internal/account-holders",
        ),
    )
}
