package com.malai.stub.scheme

import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/scenarios/collections")
class StubScenariosCollectionController(
    private val bacsCollectionNotifier: StubBacsCollectionNotifier,
) {

    @PostMapping("/bacs", consumes = [APPLICATION_JSON_VALUE])
    fun bacsCollection(@RequestBody body: CollectionRequest): ResponseEntity<Void> {
        body.requireValid()
        bacsCollectionNotifier.emitBacsCollection(
            endToEndId = body.endToEndId ?: UUID.randomUUID(),
            debtorSortCode = body.debtorSortCode,
            debtorAccountNumber = body.debtorAccountNumber,
            debtorName = body.debtorName,
            creditorSortCode = body.creditorSortCode,
            creditorAccountNumber = body.creditorAccountNumber,
            creditorName = body.creditorName,
            amountMinorUnits = body.amountMinorUnits,
            currency = body.currency,
            description = body.description,
        )
        return ResponseEntity.noContent().build()
    }
}

data class CollectionRequest(
    val debtorSortCode: String,
    val debtorAccountNumber: String,
    val debtorName: String,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val creditorName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val description: String?,
    val endToEndId: UUID? = null,
) {
    fun requireValid() {
        require(debtorSortCode.isNotBlank()) { "debtorSortCode is required" }
        require(debtorAccountNumber.isNotBlank()) { "debtorAccountNumber is required" }
        require(debtorName.isNotBlank()) { "debtorName is required" }
        require(creditorSortCode.isNotBlank()) { "creditorSortCode is required" }
        require(creditorAccountNumber.isNotBlank()) { "creditorAccountNumber is required" }
        require(creditorName.isNotBlank()) { "creditorName is required" }
        require(currency.isNotBlank()) { "currency is required" }
        require(amountMinorUnits > 0) { "amountMinorUnits must be positive" }
    }
}
