package com.malai.scheme.contracts.fps

import java.util.UUID

data class FpsSchemeSettlementCallback(
    val endToEndId: UUID,
    val paymentId: UUID,
)

data class FpsSchemeReturnCallback(
    val endToEndId: UUID,
    val paymentId: UUID,
    val reasonCode: String,
)
