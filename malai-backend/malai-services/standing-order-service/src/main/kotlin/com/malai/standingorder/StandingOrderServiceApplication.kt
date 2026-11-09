package com.malai.standingorder

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(StandingOrderServiceConfiguration::class)
class StandingOrderServiceApplication

fun main(args: Array<String>) {
    runApplication<StandingOrderServiceApplication>(*args)
}
