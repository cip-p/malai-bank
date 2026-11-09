package com.malai.client.malai

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@SpringBootTest(
    classes = [MalaiClientTestApplication::class],
    properties = ["spring.application.name=malai-client-tests"]
)
@ActiveProfiles("test")
annotation class MalaiClientSpringTest
