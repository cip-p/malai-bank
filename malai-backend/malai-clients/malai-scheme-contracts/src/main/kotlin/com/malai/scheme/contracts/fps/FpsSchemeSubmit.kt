package com.malai.scheme.contracts.fps

import java.util.UUID

data class FpsSchemeParty(
    val sortCode: String?,
    val accountNumber: String?,
    val name: String?,
)

data class FpsSchemeAmount(
    val minorUnits: Long,
    val currency: String,
)

data class FpsSchemeSubmitRequest(
    val endToEndId: UUID,
    val paymentId: UUID,
    val debtor: FpsSchemeParty,
    val creditor: FpsSchemeParty,
    val instructedAmount: FpsSchemeAmount,
    val remittanceInformation: String?,
    val copVerdictOverride: Boolean = false,
)

data class FpsSchemeSubmitAck(
    val endToEndId: UUID,
    val paymentId: UUID,
    val status: String = "ACCEPTED",
)
