package com.malai.swiftgateway

import com.malai.scheme.contracts.swift.SwiftSchemeSettlementCallback
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/swift/callbacks")
class SwiftCallbackController(private val eventProducer: SwiftEventProducer) {

    @PostMapping("/settlement", consumes = [APPLICATION_JSON_VALUE])
    fun settlement(@RequestBody callback: SwiftSchemeSettlementCallback): ResponseEntity<Void> {
        eventProducer.publishSettled(SwiftSchemeTranslator.toSettledEvent(callback))
        return ResponseEntity.accepted().build()
    }
}
