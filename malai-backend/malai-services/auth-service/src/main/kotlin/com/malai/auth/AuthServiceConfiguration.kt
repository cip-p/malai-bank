package com.malai.auth

import com.malai.auth.service.GeneratedDetailsGenerator
import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.MalaiCommonsConfiguration
import com.malai.commons.TimeService
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.cognito.MalaiCognitoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.security.SecureRandom

@Configuration
@ComponentScan(basePackages = ["com.malai.auth"])
@EnableJpaRepositories(basePackages = ["com.malai.auth"])
@EntityScan(basePackages = ["com.malai.auth"])
@Import(
    MalaiCognitoConfiguration::class,
    MalaiCommonsConfiguration::class,
    MalaiClientConfiguration::class,
)
class AuthServiceConfiguration {

    @Bean
    fun generatedDetailsGenerator(timeService: TimeService) =
        GeneratedDetailsGenerator(timeService, SecureRandom())

    @Bean
    fun authServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "auth-service",
        pathPrefixes = listOf("/api/v1/auth", "/api/v1/user", "/api/v1/admin"),
    )
}
