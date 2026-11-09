package com.malai.cardgateway

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CardGatewayConfiguration::class)
class CardGatewayApplication

fun main(args: Array<String>) {
    runApplication<CardGatewayApplication>(*args)
}
