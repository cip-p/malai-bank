package com.malai.payments

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceQueues
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.security.MalaiSecurityConfiguration
import com.malai.security.idempotency.IdempotencyProtectedRoutes
import com.malai.security.idempotency.IdempotencyProtectedRoutes.ProtectedPath
import com.malai.sqs.MalaiSqsMessagingConfiguration
import com.malai.sqs.payments.PaymentsQueues
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ComponentScan(basePackages = ["com.malai.payments"])
@EnableJpaRepositories(basePackages = ["com.malai.payments"])
@EntityScan(basePackages = ["com.malai.payments"])
@Import(
    MalaiSecurityConfiguration::class,
    MalaiSqsMessagingConfiguration::class,
    MalaiClientConfiguration::class,
)
class PaymentsServiceConfiguration {

    @Bean
    fun paymentsServiceIdempotencyRoutes() = IdempotencyProtectedRoutes(
        serviceName = "payments-service",
        paths = listOf(
            ProtectedPath("POST", "/api/v1/payments"),
        ),
    )

    @Bean
    fun paymentsServiceSourceRoutes() = MalaiSourceRoutes(
        serviceName = "payments-service",
        pathPrefixes = listOf("/api/v1/payments"),
    )

    @Bean
    fun paymentsServiceSourceQueues() = MalaiSourceQueues(
        serviceName = "payments-service",
        queueBaseNames = listOf(
            PaymentsQueues.FPS_EVENT_SETTLED,
            PaymentsQueues.FPS_EVENT_RETURNED,
            PaymentsQueues.FPS_EVENT_RECEIVED,
            PaymentsQueues.SWIFT_EVENT_SETTLED,
            PaymentsQueues.BACS_EVENT_RECEIVED,
            PaymentsQueues.BACS_EVENT_COLLECTION,
        ),
    )
}
