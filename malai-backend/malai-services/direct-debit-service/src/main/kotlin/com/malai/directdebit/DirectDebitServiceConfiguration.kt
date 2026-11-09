package com.malai.directdebit

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.directdebit"])
@EnableJpaRepositories(basePackages = ["com.malai.directdebit"])
@EntityScan(basePackages = ["com.malai.directdebit"])
@Import(MalaiSecurityConfiguration::class, MalaiClientConfiguration::class)
class DirectDebitServiceConfiguration {

    @Bean
    fun directDebitServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "direct-debit-service",
        pathPrefixes = listOf("/api/v1/direct-debits"),
    )
}
