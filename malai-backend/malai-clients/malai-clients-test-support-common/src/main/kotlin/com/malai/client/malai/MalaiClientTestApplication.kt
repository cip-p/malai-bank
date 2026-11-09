package com.malai.client.malai

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(MalaiClientConfiguration::class)
@PropertySource("classpath:malai-client-test.properties")
class MalaiClientTestApplication
