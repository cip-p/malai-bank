package com.malai.cardgateway

import com.malai.client.malai.card.CardClient
import com.malai.scheme.contracts.card.CardSchemeAuthorisationRequest
import com.malai.scheme.contracts.card.CardSchemeAuthorisationResponse
import com.malai.scheme.contracts.card.CardSchemePresentmentRequest
import com.malai.scheme.contracts.card.CardSchemeRefundRequest
import com.malai.scheme.contracts.card.CardSchemeReversalRequest
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cards/callbacks")
class CardCallbackController(
    private val cardClient: CardClient,
    private val cardGatewayServiceTokenClient: CardGatewayServiceTokenClient,
    private val cardEventProducer: CardEventProducer,
) {

    @PostMapping("/authorise", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun authorise(@RequestBody request: CardSchemeAuthorisationRequest): CardSchemeAuthorisationResponse {
        val decision = cardClient.authorise(
            cardGatewayServiceTokenClient.getToken(),
            CardSchemeTranslator.toInternal(request),
        )
        return CardSchemeTranslator.toSchemeResponse(request, decision)
    }

    @PostMapping("/present", consumes = [APPLICATION_JSON_VALUE])
    fun present(@RequestBody request: CardSchemePresentmentRequest): ResponseEntity<Void> {
        cardEventProducer.publishPresented(CardSchemeTranslator.toPresentmentNotify(request))
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/reverse", consumes = [APPLICATION_JSON_VALUE])
    fun reverse(@RequestBody request: CardSchemeReversalRequest): ResponseEntity<Void> {
        cardEventProducer.publishReversed(CardSchemeTranslator.toReversalNotify(request))
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/refund", consumes = [APPLICATION_JSON_VALUE])
    fun refund(@RequestBody request: CardSchemeRefundRequest): ResponseEntity<Void> {
        cardEventProducer.publishRefunded(CardSchemeTranslator.toRefundNotify(request))
        return ResponseEntity.accepted().build()
    }
}
