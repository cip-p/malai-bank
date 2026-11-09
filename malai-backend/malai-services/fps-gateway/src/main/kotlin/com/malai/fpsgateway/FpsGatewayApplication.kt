package com.malai.fpsgateway

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(FpsGatewayConfiguration::class)
class FpsGatewayApplication

fun main(args: Array<String>) {
    runApplication<FpsGatewayApplication>(*args)
}
