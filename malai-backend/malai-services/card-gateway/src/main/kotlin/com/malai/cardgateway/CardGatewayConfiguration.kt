package com.malai.cardgateway

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.tracking.MalaiSourceRoutes
import com.malai.sqs.MalaiSqsMessagingConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ComponentScan(basePackages = ["com.malai.cardgateway"])
@Import(MalaiClientConfiguration::class, MalaiSqsMessagingConfiguration::class)
class CardGatewayConfiguration {

    @Bean
    fun cardGatewaySourceRoutes() = MalaiSourceRoutes(
        serviceName = "card-gateway",
        pathPrefixes = listOf("/cards/callbacks"),
    )
}
