package com.malai.cardgateway

import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.messages.CardRefundNotify
import com.malai.card.messages.CardReversalNotify
import com.malai.client.malai.card.model.AuthorisationDecision.APPROVED
import com.malai.client.malai.card.model.AuthorisationDecision.DECLINED
import com.malai.client.malai.card.model.AuthoriseCardRequest
import com.malai.client.malai.card.model.AuthoriseCardResponse
import com.malai.scheme.contracts.card.CardDeclineReasonCodes
import com.malai.scheme.contracts.card.CardSchemeAuthorisationOutcome
import com.malai.scheme.contracts.card.CardSchemeAuthorisationRequest
import com.malai.scheme.contracts.card.CardSchemeAuthorisationResponse
import com.malai.scheme.contracts.card.CardSchemePresentmentRequest
import com.malai.scheme.contracts.card.CardSchemeRefundRequest
import com.malai.scheme.contracts.card.CardSchemeReversalRequest

object CardSchemeTranslator {

    fun toInternal(request: CardSchemeAuthorisationRequest): AuthoriseCardRequest =
        AuthoriseCardRequest(
            schemeReference = request.schemeReference,
            pan = request.pan,
            amountMinorUnits = request.amount.minorUnits,
            currency = request.amount.currency,
            merchantName = request.merchantName,
            mcc = request.mcc,
        )

    fun toPresentmentNotify(request: CardSchemePresentmentRequest): CardPresentmentNotify =
        CardPresentmentNotify(
            schemeReference = request.schemeReference,
            presentedAmountMinorUnits = request.amount.minorUnits,
            currency = request.amount.currency,
            merchantName = request.merchantName,
        )

    fun toReversalNotify(request: CardSchemeReversalRequest): CardReversalNotify =
        CardReversalNotify(
            schemeReference = request.schemeReference,
        )

    fun toRefundNotify(request: CardSchemeRefundRequest): CardRefundNotify =
        CardRefundNotify(
            schemeReference = request.schemeReference,
            refundReference = request.refundReference,
            refundAmountMinorUnits = request.amount.minorUnits,
            currency = request.amount.currency,
        )

    fun toSchemeResponse(
        request: CardSchemeAuthorisationRequest,
        response: AuthoriseCardResponse,
    ): CardSchemeAuthorisationResponse = when (response.decision) {
        APPROVED -> CardSchemeAuthorisationResponse(
            schemeReference = request.schemeReference,
            outcome = CardSchemeAuthorisationOutcome.APPROVED,
        )
        DECLINED -> CardSchemeAuthorisationResponse(
            schemeReference = request.schemeReference,
            outcome = CardSchemeAuthorisationOutcome.DECLINED,
            declineReasonCode = CardDeclineReasonCodes.schemeCode(response.declineReason!!.name),
        )
    }
}
