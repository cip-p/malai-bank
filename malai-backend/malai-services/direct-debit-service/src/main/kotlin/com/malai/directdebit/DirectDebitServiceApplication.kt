package com.malai.directdebit

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(DirectDebitServiceConfiguration::class)
class DirectDebitServiceApplication

fun main(args: Array<String>) {
    runApplication<DirectDebitServiceApplication>(*args)
}
