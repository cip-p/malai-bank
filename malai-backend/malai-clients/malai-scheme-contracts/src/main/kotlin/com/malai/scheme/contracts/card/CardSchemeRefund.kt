package com.malai.scheme.contracts.card

import java.util.UUID

data class CardSchemeRefundRequest(
    val schemeReference: UUID,
    val refundReference: UUID,
    val amount: CardSchemeAmount,
)
