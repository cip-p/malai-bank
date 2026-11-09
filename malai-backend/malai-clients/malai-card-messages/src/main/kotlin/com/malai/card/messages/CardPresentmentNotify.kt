package com.malai.card.messages

import java.util.UUID

data class CardPresentmentNotify(
    val messageType: String = "card.presentment.notify",
    val schemeReference: UUID,
    val presentedAmountMinorUnits: Long,
    val currency: String,
    val merchantName: String,
)
