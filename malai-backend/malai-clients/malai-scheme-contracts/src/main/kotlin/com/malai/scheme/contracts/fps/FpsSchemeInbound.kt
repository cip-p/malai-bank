package com.malai.scheme.contracts.fps

import java.util.UUID

data class FpsSchemeInboundNotification(
    val endToEndId: UUID,
    val creditor: FpsSchemeParty,
    val debtor: FpsSchemeParty,
    val instructedAmount: FpsSchemeAmount,
    val remittanceInformation: String?,
)

data class FpsSchemeReturnToSender(
    val endToEndId: UUID,
    val creditor: FpsSchemeParty,
    val reasonCode: String,
)
