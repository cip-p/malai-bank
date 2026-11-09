package com.malai.insights

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(InsightsServiceConfiguration::class)
class InsightsServiceApplication

fun main(args: Array<String>) {
    runApplication<InsightsServiceApplication>(*args)
}
