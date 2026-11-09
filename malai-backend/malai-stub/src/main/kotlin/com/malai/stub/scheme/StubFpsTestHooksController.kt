package com.malai.stub.scheme

import com.malai.payments.messages.ReturnReason
import com.malai.scheme.contracts.fps.FpsSchemeReturnToSender
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test-hooks")
class StubFpsTestHooksController(private val stubFpsScheme: StubFpsScheme) {

    @PostMapping("/arm-return", consumes = [APPLICATION_JSON_VALUE])
    fun armReturn(@RequestBody body: ArmReturnRequest): ResponseEntity<Void> {
        stubFpsScheme.armReturn(body.reason)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/fps/returns", produces = [APPLICATION_JSON_VALUE])
    fun receivedReturns(): ResponseEntity<List<FpsSchemeReturnToSender>> =
        ResponseEntity.ok(stubFpsScheme.receivedReturns())
}

data class ArmReturnRequest(val reason: ReturnReason)
