package com.malai.stub

import com.malai.client.malai.MalaiClientConfiguration
import com.malai.commons.MalaiCommonsConfiguration
import com.malai.sqs.MalaiSqsMessagingConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@Import(
    MalaiCommonsConfiguration::class,
    MalaiClientConfiguration::class,
    MalaiSqsMessagingConfiguration::class,
)
class MalaiStubApplication

fun main(args: Array<String>) {
    runApplication<MalaiStubApplication>(*args)
}
