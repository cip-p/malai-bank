package com.malai.commons.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

fun interface UuidGenerator {
    fun generate(): UUID
}

@Configuration
class UuidGeneratorConfiguration {

    @Bean
    fun uuidGenerator(): UuidGenerator = UuidGenerator { UUID.randomUUID() }
}
