package com.malai.fpsgateway

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
@ComponentScan(basePackages = ["com.malai.fpsgateway"])
@Import(
    MalaiSqsMessagingConfiguration::class,
    MalaiClientConfiguration::class,
)
class FpsGatewayConfiguration {

    @Bean
    fun fpsGatewaySourceRoutes() = MalaiSourceRoutes(
        serviceName = "fps-gateway",
        pathPrefixes = listOf("/fps/callbacks"),
    )

    @Bean
    fun fpsGatewaySourceQueues() = MalaiSourceQueues(
        serviceName = "fps-gateway",
        queueBaseNames = listOf(
            PaymentsQueues.FPS_COMMAND_SUBMIT,
            PaymentsQueues.FPS_COMMAND_RETURN,
        ),
    )
}
