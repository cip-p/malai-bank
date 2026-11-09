package com.malai.copilot

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CopilotServiceConfiguration::class)
class CopilotServiceApplication

fun main(args: Array<String>) {
    runApplication<CopilotServiceApplication>(*args)
}
