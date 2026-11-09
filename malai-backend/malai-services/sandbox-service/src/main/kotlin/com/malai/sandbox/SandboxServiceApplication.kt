package com.malai.sandbox

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SandboxServiceConfiguration::class)
class SandboxServiceApplication

fun main(args: Array<String>) {
    runApplication<SandboxServiceApplication>(*args)
}
