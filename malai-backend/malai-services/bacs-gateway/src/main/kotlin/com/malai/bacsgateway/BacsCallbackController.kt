package com.malai.bacsgateway

import com.malai.scheme.contracts.bacs.BacsSchemeDirectCredit
import com.malai.scheme.contracts.bacs.BacsSchemeDirectDebitCollection
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/bacs/callbacks")
class BacsCallbackController(private val eventProducer: BacsEventProducer) {

    @PostMapping("/inbound", consumes = [APPLICATION_JSON_VALUE])
    fun inbound(@RequestBody credit: BacsSchemeDirectCredit): ResponseEntity<Void> {
        eventProducer.publishReceived(BacsSchemeTranslator.toInboundNotify(credit))
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/collection", consumes = [APPLICATION_JSON_VALUE])
    fun collection(@RequestBody collection: BacsSchemeDirectDebitCollection): ResponseEntity<Void> {
        eventProducer.publishCollection(BacsSchemeTranslator.toCollectionNotify(collection))
        return ResponseEntity.accepted().build()
    }
}
