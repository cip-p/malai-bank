package com.malai.swiftgateway

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SwiftGatewayConfiguration::class)
class SwiftGatewayApplication

fun main(args: Array<String>) {
    runApplication<SwiftGatewayApplication>(*args)
}
