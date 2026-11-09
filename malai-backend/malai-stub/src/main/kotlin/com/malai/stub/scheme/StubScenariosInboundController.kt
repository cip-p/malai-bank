package com.malai.stub.scheme

import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/scenarios/inbound")
class StubScenariosInboundController(
    private val fpsInboundNotifier: StubFpsInboundNotifier,
    private val bacsInboundNotifier: StubBacsInboundNotifier,
) {

    @PostMapping("/fps", consumes = [APPLICATION_JSON_VALUE])
    fun fpsInbound(@RequestBody body: InboundRequest): ResponseEntity<Void> {
        body.requireValid()
        fpsInboundNotifier.emitFpsInbound(
            beneficiarySortCode = body.beneficiarySortCode,
            beneficiaryAccountNumber = body.beneficiaryAccountNumber,
            beneficiaryName = body.beneficiaryName,
            originatorSortCode = body.originatorSortCode,
            originatorAccountNumber = body.originatorAccountNumber,
            originatorName = body.originatorName,
            amountMinorUnits = body.amountMinorUnits,
            currency = body.currency,
            description = body.description,
        )
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/bacs", consumes = [APPLICATION_JSON_VALUE])
    fun bacsInbound(@RequestBody body: InboundRequest): ResponseEntity<Void> {
        body.requireValid()
        bacsInboundNotifier.emitBacsInbound(
            beneficiarySortCode = body.beneficiarySortCode,
            beneficiaryAccountNumber = body.beneficiaryAccountNumber,
            beneficiaryName = body.beneficiaryName,
            originatorSortCode = body.originatorSortCode,
            originatorAccountNumber = body.originatorAccountNumber,
            originatorName = body.originatorName,
            amountMinorUnits = body.amountMinorUnits,
            currency = body.currency,
            description = body.description,
        )
        return ResponseEntity.noContent().build()
    }
}

data class InboundRequest(
    val beneficiarySortCode: String,
    val beneficiaryAccountNumber: String,
    val beneficiaryName: String,
    val originatorSortCode: String,
    val originatorAccountNumber: String,
    val originatorName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val description: String?,
) {
    fun requireValid() {
        require(beneficiarySortCode.isNotBlank()) { "beneficiarySortCode is required" }
        require(beneficiaryAccountNumber.isNotBlank()) { "beneficiaryAccountNumber is required" }
        require(beneficiaryName.isNotBlank()) { "beneficiaryName is required" }
        require(originatorSortCode.isNotBlank()) { "originatorSortCode is required" }
        require(originatorAccountNumber.isNotBlank()) { "originatorAccountNumber is required" }
        require(originatorName.isNotBlank()) { "originatorName is required" }
        require(currency.isNotBlank()) { "currency is required" }
        require(amountMinorUnits > 0) { "amountMinorUnits must be positive" }
    }
}

