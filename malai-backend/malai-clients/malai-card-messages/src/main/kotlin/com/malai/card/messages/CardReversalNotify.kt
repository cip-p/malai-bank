package com.malai.card.messages

import java.util.UUID

data class CardReversalNotify(
    val messageType: String = "card.reversal.notify",
    val schemeReference: UUID,
)
