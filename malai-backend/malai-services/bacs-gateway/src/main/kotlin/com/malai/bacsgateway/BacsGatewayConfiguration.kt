package com.malai.bacsgateway

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.MalaiCommonsConfiguration
import com.malai.commons.tracking.MalaiSourceQueues
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.sqs.MalaiSqsMessagingConfiguration
import com.malai.sqs.payments.PaymentsQueues
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ComponentScan(basePackages = ["com.malai.bacsgateway"])
@Import(
    MalaiCommonsConfiguration::class,
    MalaiSqsMessagingConfiguration::class,
    MalaiClientConfiguration::class,
)
class BacsGatewayConfiguration {

    @Bean
    fun bacsGatewaySourceRoutes() = MalaiSourceRoutes(
        serviceName = "bacs-gateway",
        pathPrefixes = listOf("/bacs/callbacks"),
    )

    @Bean
    fun bacsGatewaySourceQueues() = MalaiSourceQueues(
        serviceName = "bacs-gateway",
        queueBaseNames = listOf(
            PaymentsQueues.BACS_COMMAND_RETURN,
        ),
    )
}
