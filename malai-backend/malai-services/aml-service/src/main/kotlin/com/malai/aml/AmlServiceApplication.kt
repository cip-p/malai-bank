package com.malai.aml

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(AmlServiceConfiguration::class)
class AmlServiceApplication

fun main(args: Array<String>) {
    runApplication<AmlServiceApplication>(*args)
}
