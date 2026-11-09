package com.malai.scheme.contracts.card

import java.util.UUID

data class CardSchemePresentmentRequest(
    val schemeReference: UUID,
    val amount: CardSchemeAmount,
    val merchantName: String,
)
