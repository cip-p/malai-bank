package com.malai.accounts

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(AccountsServiceConfiguration::class)
class AccountsServiceApplication

fun main(args: Array<String>) {
    runApplication<AccountsServiceApplication>(*args)
}
