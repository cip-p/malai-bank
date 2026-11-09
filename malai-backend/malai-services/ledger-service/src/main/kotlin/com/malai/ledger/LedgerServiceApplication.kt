package com.malai.ledger

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(LedgerServiceConfiguration::class)
class LedgerServiceApplication

fun main(args: Array<String>) {
    runApplication<LedgerServiceApplication>(*args)
}
