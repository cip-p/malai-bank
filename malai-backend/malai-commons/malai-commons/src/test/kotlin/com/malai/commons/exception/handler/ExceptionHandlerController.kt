package com.malai.commons.exception.handler

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ExceptionHandlerController(private val service: ExceptionHandlerService) {

    @GetMapping(value = ["test-exception-handler"])
    fun testExceptionHandler() {
        service.doSomething()
    }

    @PostMapping(value = ["test-exception-handler-with-body"])
    fun testExceptionHandler(@RequestBody testRequestBody: TestRequestBody) {
        service.doSomething()
    }

}

data class TestRequestBody(
    private val field1: String,
    private val field2: String
)
