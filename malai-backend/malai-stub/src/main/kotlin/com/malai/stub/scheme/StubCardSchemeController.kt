package com.malai.stub.scheme

import com.malai.scheme.contracts.card.CardSchemeAuthorisationResponse
import com.malai.stub.directory.StubMerchantDirectory
import com.malai.stub.directory.StubPanRegistry
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/scenarios/cards")
class StubCardSchemeController(
    private val stubCardScheme: StubCardScheme,
    private val merchants: StubMerchantDirectory,
    private val panRegistry: StubPanRegistry,
) {

    @GetMapping("/merchants", produces = [APPLICATION_JSON_VALUE])
    fun merchants(): CardMerchantsResponse = CardMerchantsResponse(
        merchants = merchants.all().map { CardMerchantResponse(it.merchantId, it.name, it.mcc) }
    )

    @PostMapping("/pans", consumes = [APPLICATION_JSON_VALUE])
    fun registerPan(@RequestBody body: RegisterPanRequest): ResponseEntity<Void> {
        body.requireValid()
        panRegistry.register(body.pan)
        return ResponseEntity.accepted().build()
    }

    @GetMapping("/pans", produces = [APPLICATION_JSON_VALUE])
    fun pans(): RegisteredPansResponse = RegisteredPansResponse(pans = panRegistry.all())

    @PostMapping("/purchase", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun purchase(@RequestBody body: CardPurchaseRequest): ResponseEntity<CardSchemeAuthorisationResponse> {
        body.requireValid()
        return ResponseEntity.ok(
            stubCardScheme.originateAuthorisation(
                pan = body.pan,
                merchantId = body.merchantId,
                amountMinorUnits = body.amountMinorUnits,
                currency = body.currency,
            )
        )
    }

    @PostMapping("/present-now", produces = [APPLICATION_JSON_VALUE])
    fun presentNow(): ResponseEntity<PresentNowResponse> =
        ResponseEntity.ok(PresentNowResponse(presented = stubCardScheme.presentAllPending()))

    @PostMapping("/reverse", consumes = [APPLICATION_JSON_VALUE])
    fun reverse(@RequestBody body: CardReverseRequest): ResponseEntity<Void> {
        body.requireValid()
        stubCardScheme.reverse(schemeReference = UUID.fromString(body.schemeReference))
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/refund", consumes = [APPLICATION_JSON_VALUE])
    fun refund(@RequestBody body: CardRefundRequest): ResponseEntity<Void> {
        body.requireValid()
        stubCardScheme.refund(
            schemeReference = UUID.fromString(body.schemeReference),
            amountMinorUnits = body.amountMinorUnits,
            currency = body.currency,
        )
        return ResponseEntity.accepted().build()
    }
}

data class CardMerchantsResponse(val merchants: List<CardMerchantResponse>)

data class CardMerchantResponse(val merchantId: String, val name: String, val mcc: String)

data class RegisterPanRequest(val pan: String) {
    fun requireValid() {
        require(pan.isNotBlank()) { "pan is required" }
    }
}

data class RegisteredPansResponse(val pans: List<String>)

data class PresentNowResponse(val presented: Int)

data class CardReverseRequest(
    val schemeReference: String,
) {
    fun requireValid() {
        require(schemeReference.isNotBlank()) { "schemeReference is required" }
    }
}

data class CardRefundRequest(
    val schemeReference: String,
    val amountMinorUnits: Long,
    val currency: String,
) {
    fun requireValid() {
        require(schemeReference.isNotBlank()) { "schemeReference is required" }
        require(currency.isNotBlank()) { "currency is required" }
        require(amountMinorUnits > 0) { "amountMinorUnits must be positive" }
    }
}

data class CardPurchaseRequest(
    val pan: String,
    val merchantId: String,
    val amountMinorUnits: Long,
    val currency: String,
) {
    fun requireValid() {
        require(pan.isNotBlank()) { "pan is required" }
        require(merchantId.isNotBlank()) { "merchantId is required" }
        require(currency.isNotBlank()) { "currency is required" }
        require(amountMinorUnits > 0) { "amountMinorUnits must be positive" }
    }
}
