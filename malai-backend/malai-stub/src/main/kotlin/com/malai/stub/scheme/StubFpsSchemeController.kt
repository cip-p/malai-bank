package com.malai.stub.scheme

import com.malai.scheme.contracts.fps.FpsSchemeReturnToSender
import com.malai.scheme.contracts.fps.FpsSchemeSubmitAck
import com.malai.scheme.contracts.fps.FpsSchemeSubmitRequest
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/fps")
class StubFpsSchemeController(private val stubFpsScheme: StubFpsScheme) {

    @PostMapping("/payments", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun submit(@RequestBody request: FpsSchemeSubmitRequest): ResponseEntity<FpsSchemeSubmitAck> =
        ResponseEntity.status(ACCEPTED).body(stubFpsScheme.submit(request))

    @PostMapping("/returns", consumes = [APPLICATION_JSON_VALUE])
    fun returnToSender(@RequestBody request: FpsSchemeReturnToSender): ResponseEntity<Void> {
        stubFpsScheme.receiveReturnToSender(request)
        return ResponseEntity.status(ACCEPTED).build()
    }
}
