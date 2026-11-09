package com.malai.payee

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(PayeeServiceConfiguration::class)
class PayeeServiceApplication

fun main(args: Array<String>) {
    runApplication<PayeeServiceApplication>(*args)
}
