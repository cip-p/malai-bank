package com.malai.stub.scheme

import com.malai.scheme.contracts.swift.SwiftSchemeSubmitAck
import com.malai.scheme.contracts.swift.SwiftSchemeSubmitRequest
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/swift")
class StubSwiftSchemeController(private val stubSwiftScheme: StubSwiftScheme) {

    @PostMapping("/payments", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun submit(@RequestBody request: SwiftSchemeSubmitRequest): ResponseEntity<SwiftSchemeSubmitAck> =
        ResponseEntity.status(ACCEPTED).body(stubSwiftScheme.submit(request))
}
