package com.malai.fpsgateway

import com.malai.scheme.contracts.fps.FpsSchemeInboundNotification
import com.malai.scheme.contracts.fps.FpsSchemeReturnCallback
import com.malai.scheme.contracts.fps.FpsSchemeSettlementCallback
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/fps/callbacks")
class FpsCallbackController(private val eventProducer: FpsEventProducer) {

    @PostMapping("/settlement", consumes = [APPLICATION_JSON_VALUE])
    fun settlement(@RequestBody callback: FpsSchemeSettlementCallback): ResponseEntity<Void> {
        eventProducer.publishSettled(FpsSchemeTranslator.toSettledEvent(callback))
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/return", consumes = [APPLICATION_JSON_VALUE])
    fun returned(@RequestBody callback: FpsSchemeReturnCallback): ResponseEntity<Void> {
        eventProducer.publishReturned(FpsSchemeTranslator.toReturnedEvent(callback))
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/inbound", consumes = [APPLICATION_JSON_VALUE])
    fun inbound(@RequestBody notification: FpsSchemeInboundNotification): ResponseEntity<Void> {
        eventProducer.publishReceived(FpsSchemeTranslator.toInboundNotify(notification))
        return ResponseEntity.accepted().build()
    }
}
