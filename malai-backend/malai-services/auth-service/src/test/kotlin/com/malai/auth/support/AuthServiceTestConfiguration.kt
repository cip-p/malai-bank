package com.malai.auth.support

import com.malai.auth.service.GeneratedDetailsGenerator
import com.malai.commons.TimeService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test", "acceptance-tests")
class AuthServiceTestConfiguration {

    @Bean
    @Primary
    fun stubbedGeneratedDetailsGenerator(timeService: TimeService): GeneratedDetailsGenerator =
        StubbedGeneratedDetailsGenerator(timeService)
}
