package com.malai.ledger

import com.malai.commons.MalaiCommonsConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.ledger"])
@EnableJpaRepositories(basePackages = ["com.malai.ledger"])
@EntityScan(basePackages = ["com.malai.ledger"])
@Import(MalaiCommonsConfiguration::class)
class LedgerServiceConfiguration {

    @Bean
    fun ledgerServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "ledger-service",
        pathPrefixes = listOf("/api/v1/internal/ledger"),
    )
}
