package com.malai.exec

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ExecServiceConfiguration::class)
class ExecServiceApplication

fun main(args: Array<String>) {
    runApplication<ExecServiceApplication>(*args)
}
