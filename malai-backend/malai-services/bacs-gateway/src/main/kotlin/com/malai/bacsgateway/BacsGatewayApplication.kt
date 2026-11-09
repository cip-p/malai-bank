package com.malai.bacsgateway

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(BacsGatewayConfiguration::class)
class BacsGatewayApplication

fun main(args: Array<String>) {
    runApplication<BacsGatewayApplication>(*args)
}
