package com.malai.swiftgateway

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceQueues
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.sqs.MalaiSqsMessagingConfiguration
import com.malai.sqs.payments.PaymentsQueues
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ComponentScan(basePackages = ["com.malai.swiftgateway"])
@Import(
    MalaiSqsMessagingConfiguration::class,
    MalaiClientConfiguration::class,
)
class SwiftGatewayConfiguration {

    @Bean
    fun swiftGatewaySourceRoutes() = MalaiSourceRoutes(
        serviceName = "swift-gateway",
        pathPrefixes = listOf("/swift/callbacks"),
    )

    @Bean
    fun swiftGatewaySourceQueues() = MalaiSourceQueues(
        serviceName = "swift-gateway",
        queueBaseNames = listOf(
            PaymentsQueues.SWIFT_COMMAND_SUBMIT,
        ),
    )
}
