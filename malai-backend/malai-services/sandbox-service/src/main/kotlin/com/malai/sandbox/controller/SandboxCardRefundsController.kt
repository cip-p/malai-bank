package com.malai.sandbox.controller

import com.malai.client.malai.card.CardClient
import com.malai.client.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.client.malai.stub.MalaiStubClient
import com.malai.client.malai.stub.card.model.CardRefundForwardPayload
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.CARD_NOT_FOUND
import com.malai.commons.exception.MalaiCode.CARD_PURCHASE_NOT_SETTLED
import com.malai.commons.exception.MalaiCode.GENERIC_BAD_REQUEST
import com.malai.commons.exception.NotFoundException
import com.malai.sandbox.service.SandboxServiceTokenClient
import com.malai.security.MalaiUser
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sandbox/card-refunds")
class SandboxCardRefundsController(
    private val stubClient: MalaiStubClient,
    private val cardClient: CardClient,
    private val sandboxServiceTokenClient: SandboxServiceTokenClient,
) {

    @PostMapping(consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(ACCEPTED)
    fun trigger(
        @AuthenticationPrincipal user: MalaiUser,
        @RequestBody request: SandboxCardRefundRequest,
    ) {
        val holder = user as? MalaiUserWithAccountHolderId
            ?: throw BadRequestException(GENERIC_BAD_REQUEST, "MAL-Account-Holder-Id header is required")

        val authorisation = cardClient.resolveAuthorisation(
            sandboxServiceTokenClient.getToken(),
            request.authorisationId,
            holder.accountHolderId,
        ) ?: throw NotFoundException(CARD_NOT_FOUND)

        if (authorisation.status != SETTLED) {
            throw BadRequestException(CARD_PURCHASE_NOT_SETTLED, "only a settled purchase can be refunded")
        }

        stubClient.triggerCardRefund(
            CardRefundForwardPayload(
                schemeReference = authorisation.schemeReference,
                amountMinorUnits = authorisation.amountMinorUnits,
                currency = authorisation.currency,
            ),
        )
    }
}

data class SandboxCardRefundRequest(
    val authorisationId: UUID,
)
