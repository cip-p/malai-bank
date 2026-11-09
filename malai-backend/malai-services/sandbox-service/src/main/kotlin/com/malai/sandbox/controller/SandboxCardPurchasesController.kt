package com.malai.sandbox.controller

import com.malai.client.malai.card.CardClient
import com.malai.client.malai.stub.MalaiStubClient
import com.malai.client.malai.stub.card.model.CardPurchaseForwardPayload
import com.malai.client.malai.stub.card.model.CardPurchaseResult
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.CARD_NOT_FOUND
import com.malai.commons.exception.MalaiCode.GENERIC_BAD_REQUEST
import com.malai.commons.exception.NotFoundException
import com.malai.commons.model.Currency
import com.malai.sandbox.service.SandboxServiceTokenClient
import com.malai.security.MalaiUser
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sandbox/card-purchases")
class SandboxCardPurchasesController(
    private val stubClient: MalaiStubClient,
    private val cardClient: CardClient,
    private val sandboxServiceTokenClient: SandboxServiceTokenClient,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun trigger(
        @AuthenticationPrincipal user: MalaiUser,
        @RequestBody request: SandboxCardPurchaseRequest,
    ): CardPurchaseResult {
        val holder = user as? MalaiUserWithAccountHolderId
            ?: throw BadRequestException(GENERIC_BAD_REQUEST, "MAL-Account-Holder-Id header is required")

        request.requireValid()

        val card = cardClient.resolveCard(
            sandboxServiceTokenClient.getToken(),
            request.cardId,
            holder.accountHolderId,
        ) ?: throw NotFoundException(CARD_NOT_FOUND)

        return stubClient.triggerCardPurchase(
            CardPurchaseForwardPayload(
                pan = card.pan,
                merchantId = request.merchantId!!,
                amountMinorUnits = request.amountMinorUnits!!,
                currency = Currency.GBP.name,
            ),
        )
    }
}

data class SandboxCardPurchaseRequest(
    val cardId: UUID,
    val merchantId: String?,
    val amountMinorUnits: Long?,
) {
    fun requireValid() {
        if (merchantId.isNullOrBlank()) throw BadRequestException(GENERIC_BAD_REQUEST, "merchantId is required")
        if (amountMinorUnits == null || amountMinorUnits <= 0) throw BadRequestException(GENERIC_BAD_REQUEST, "amountMinorUnits must be > 0")
    }
}
