package com.malai.security

import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test-cors")
class TestCorsController {


    @GetMapping(value = ["get"], produces = [TEXT_PLAIN_VALUE])
    fun corsGet(): ResponseEntity<String> {

        return ResponseEntity.ok("GET /test-cors/get endpoint was called")
    }

    @PostMapping(value = ["post"], produces = [TEXT_PLAIN_VALUE])
    fun corsPost(): ResponseEntity<String> {

        return ResponseEntity.ok("POST /test-cors/post endpoint was called")
    }

}
