package com.malai.stub.scheme

import com.malai.scheme.contracts.bacs.BacsSchemeCollectionReturn
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/bacs/collections")
class StubBacsCollectionReturnController {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/returns", consumes = [APPLICATION_JSON_VALUE])
    fun returns(@RequestBody returned: BacsSchemeCollectionReturn): ResponseEntity<Void> {
        log.info(
            "stub bacs scheme: collection returned to originator endToEndId={} creditor={}/{} reasonCode={}",
            returned.endToEndId, returned.creditor.sortCode, returned.creditor.accountNumber, returned.reasonCode,
        )
        return ResponseEntity.accepted().build()
    }
}
