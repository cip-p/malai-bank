package com.malai.accountholder

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(AccountHolderServiceConfiguration::class)
class AccountHolderServiceApplication

fun main(args: Array<String>) {
    runApplication<AccountHolderServiceApplication>(*args)
}
