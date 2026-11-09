package com.malai.card.messages

import java.util.UUID

data class CardRefundNotify(
    val messageType: String = "card.refund.notify",
    val schemeReference: UUID,
    val refundReference: UUID,
    val refundAmountMinorUnits: Long,
    val currency: String,
)
