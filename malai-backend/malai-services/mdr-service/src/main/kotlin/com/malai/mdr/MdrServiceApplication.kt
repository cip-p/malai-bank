package com.malai.mdr

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(MdrServiceConfiguration::class)
class MdrServiceApplication

fun main(args: Array<String>) {
    runApplication<MdrServiceApplication>(*args)
}
