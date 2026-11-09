package com.malai.test.commons

import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.test.commons.support.StubbedTimeService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test", "acceptance-tests")
class MalaiServiceTestConfiguration {

    @Bean
    @Primary
    fun stubbedTimeService(): TimeService = StubbedTimeService()

    @Bean
    @Primary
    fun testUuidGenerator(): UuidGenerator = TestUuidGenerator()
}
