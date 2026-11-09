package com.malai.card

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceQueues
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import com.malai.security.idempotency.IdempotencyProtectedRoutes
import com.malai.security.idempotency.IdempotencyProtectedRoutes.ProtectedPath
import com.malai.sqs.MalaiSqsMessagingConfiguration
import com.malai.sqs.cards.CardsQueues
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.card"])
@EnableJpaRepositories(basePackages = ["com.malai.card"])
@EntityScan(basePackages = ["com.malai.card"])
@Import(
    MalaiSecurityConfiguration::class,
    MalaiClientConfiguration::class,
    MalaiSqsMessagingConfiguration::class,
)
class CardServiceConfiguration {

    @Bean
    fun cardServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "card-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/cards/[^/]+/freeze"),
            ProtectedPath("POST", "/api/v1/cards/[^/]+/unfreeze"),
            ProtectedPath("POST", "/api/v1/cards/[^/]+/report-lost-or-stolen"),
        ),
    )

    @Bean
    fun cardServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "card-service",
        pathPrefixes = listOf("/api/v1/cards", "/api/v1/internal/cards"),
    )

    @Bean
    fun cardServiceSourceQueues() = MalaiSourceQueues(
        serviceName = "card-service",
        queueBaseNames = listOf(
            CardsQueues.SCHEME_EVENT_PRESENTED,
            CardsQueues.SCHEME_EVENT_REVERSED,
            CardsQueues.SCHEME_EVENT_REFUNDED,
        ),
    )
}
