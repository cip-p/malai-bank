package com.malai.cardgateway

import com.malai.client.malai.card.model.AuthorisationDecision.APPROVED
import com.malai.client.malai.card.model.AuthorisationDecision.DECLINED
import com.malai.client.malai.card.model.AuthoriseCardRequest
import com.malai.client.malai.card.model.AuthoriseCardResponse
import com.malai.client.malai.card.model.DeclineReason
import com.malai.scheme.contracts.card.CardDeclineReasonCodes
import com.malai.scheme.contracts.card.CardSchemeAmount
import com.malai.scheme.contracts.card.CardSchemeAuthorisationOutcome.APPROVED as SCHEME_APPROVED
import com.malai.scheme.contracts.card.CardSchemeAuthorisationOutcome.DECLINED as SCHEME_DECLINED
import com.malai.scheme.contracts.card.CardSchemeAuthorisationRequest
import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.messages.CardRefundNotify
import com.malai.card.messages.CardReversalNotify
import com.malai.scheme.contracts.card.CardSchemePresentmentRequest
import com.malai.scheme.contracts.card.CardSchemeRefundRequest
import com.malai.scheme.contracts.card.CardSchemeReversalRequest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CardSchemeTranslatorTest {

    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000f1")
    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")

    private val request = CardSchemeAuthorisationRequest(
        schemeReference = schemeReference,
        pan = "9904320000012341",
        amount = CardSchemeAmount(4_370, "GBP"),
        merchantName = "Fictional Coffee Co",
        mcc = "5814",
    )

    @Test
    fun `maps a scheme auth request onto the internal authorise request`() {
        CardSchemeTranslator.toInternal(request) shouldBe AuthoriseCardRequest(
            schemeReference = schemeReference,
            pan = "9904320000012341",
            amountMinorUnits = 4_370,
            currency = "GBP",
            merchantName = "Fictional Coffee Co",
            mcc = "5814",
        )
    }

    @Test
    fun `maps a scheme presentment request onto the internal presentment notify`() {
        CardSchemeTranslator.toPresentmentNotify(
            CardSchemePresentmentRequest(
                schemeReference = schemeReference,
                amount = CardSchemeAmount(4_370, "GBP"),
                merchantName = "Fictional Coffee Co",
            ),
        ) shouldBe CardPresentmentNotify(
            schemeReference = schemeReference,
            presentedAmountMinorUnits = 4_370,
            currency = "GBP",
            merchantName = "Fictional Coffee Co",
        )
    }

    @Test
    fun `maps a scheme reversal request onto the internal reversal notify`() {
        CardSchemeTranslator.toReversalNotify(
            CardSchemeReversalRequest(schemeReference = schemeReference),
        ) shouldBe CardReversalNotify(schemeReference = schemeReference)
    }

    @Test
    fun `maps a scheme refund request onto the internal refund notify`() {
        val refundReference = UUID.fromString("00000000-0000-4000-9000-0000000000f1")
        CardSchemeTranslator.toRefundNotify(
            CardSchemeRefundRequest(
                schemeReference = schemeReference,
                refundReference = refundReference,
                amount = CardSchemeAmount(4_370, "GBP"),
            ),
        ) shouldBe CardRefundNotify(
            schemeReference = schemeReference,
            refundReference = refundReference,
            refundAmountMinorUnits = 4_370,
            currency = "GBP",
        )
    }

    @Test
    fun `maps an approved decision onto an approved scheme response with no decline code`() {
        val response = CardSchemeTranslator.toSchemeResponse(
            request,
            AuthoriseCardResponse(authorisationId, APPROVED, null),
        )
        response.schemeReference shouldBe schemeReference
        response.outcome shouldBe SCHEME_APPROVED
        response.declineReasonCode shouldBe null
    }

    @Test
    fun `crosswalks every decline reason onto its scheme decline code`() {
        val expected = mapOf(
            DeclineReason.CARD_FROZEN to "62",
            DeclineReason.CARD_CANCELLED to "04",
            DeclineReason.INSUFFICIENT_FUNDS to "51",
            DeclineReason.OVER_LIMIT to "61",
            DeclineReason.UNKNOWN_CARD to "14",
        )
        expected.forEach { (reason, code) ->
            val response = CardSchemeTranslator.toSchemeResponse(
                request,
                AuthoriseCardResponse(authorisationId, DECLINED, reason),
            )
            response.outcome shouldBe SCHEME_DECLINED
            response.declineReasonCode shouldBe code
            CardDeclineReasonCodes.canonicalReasonName(code) shouldBe reason.name
        }
    }
}
