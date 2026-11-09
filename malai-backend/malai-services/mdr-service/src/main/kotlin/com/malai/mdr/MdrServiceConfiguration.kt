package com.malai.mdr

import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.mdr"])
@EnableJpaRepositories(basePackages = ["com.malai.mdr"])
@EntityScan(basePackages = ["com.malai.mdr"])
@Import(MalaiSecurityConfiguration::class)
class MdrServiceConfiguration {

    @Bean
    fun mdrServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "mdr-service",
        pathPrefixes = listOf(
            "/api/v1/internal/mdr",
        ),
    )
}
