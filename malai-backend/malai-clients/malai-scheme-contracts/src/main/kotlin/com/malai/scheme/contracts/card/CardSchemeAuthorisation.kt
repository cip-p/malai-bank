package com.malai.scheme.contracts.card

import java.util.UUID

data class CardSchemeAmount(
    val minorUnits: Long,
    val currency: String,
)

data class CardSchemeAuthorisationRequest(
    val schemeReference: UUID,
    val pan: String,
    val amount: CardSchemeAmount,
    val merchantName: String,
    val mcc: String,
)

enum class CardSchemeAuthorisationOutcome { APPROVED, DECLINED }

data class CardSchemeAuthorisationResponse(
    val schemeReference: UUID,
    val outcome: CardSchemeAuthorisationOutcome,
    val declineReasonCode: String? = null,
)
