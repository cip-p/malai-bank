package com.malai.payments.model

data class DepositsTotalResponse(
    val currency: String,
    val amountMinorUnits: Long,
)

data class PaymentsStoppedTotalResponse(
    val currency: String,
    val amountMinorUnits: Long,
)
