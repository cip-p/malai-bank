package com.malai.insights

import com.malai.commons.MalaiCommonsConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.insights"])
@EnableJpaRepositories(basePackages = ["com.malai.insights"])
@EntityScan(basePackages = ["com.malai.insights"])
@Import(MalaiCommonsConfiguration::class)
class InsightsServiceConfiguration {

    @Bean
    fun insightsServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "insights-service",
        pathPrefixes = listOf("/api/v1/internal/insights"),
    )
}
