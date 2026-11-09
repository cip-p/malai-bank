package com.malai.payments

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(PaymentsServiceConfiguration::class)
class PaymentsServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentsServiceApplication>(*args)
}
