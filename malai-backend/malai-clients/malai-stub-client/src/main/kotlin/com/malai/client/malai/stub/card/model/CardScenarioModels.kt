package com.malai.client.malai.stub.card.model

import java.util.UUID

data class CardPurchaseForwardPayload(
    val pan: String,
    val merchantId: String,
    val amountMinorUnits: Long,
    val currency: String,
)

enum class CardAuthorisationOutcome { APPROVED, DECLINED }

data class CardPurchaseResult(
    val schemeReference: UUID,
    val outcome: CardAuthorisationOutcome,
    val declineReasonCode: String? = null,
)

data class PresentNowResult(val presented: Int)

data class CardRefundForwardPayload(
    val schemeReference: UUID,
    val amountMinorUnits: Long,
    val currency: String,
)

data class CardMerchantsResponse(val merchants: List<CardMerchantResponse>)

data class CardMerchantResponse(
    val merchantId: String,
    val name: String,
    val mcc: String,
)
