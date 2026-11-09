package com.malai.scheme.contracts.swift

import java.util.UUID

data class SwiftSchemeParty(
    val iban: String?,
    val bic: String?,
    val name: String?,
)

data class SwiftSchemeAmount(
    val minorUnits: Long,
    val currency: String,
)

data class SwiftSchemeSubmitRequest(
    val endToEndId: UUID,
    val paymentId: UUID,
    val debtor: SwiftSchemeParty,
    val creditor: SwiftSchemeParty,
    val instructedAmount: SwiftSchemeAmount,
    val settlementAmount: SwiftSchemeAmount,
    val paymentPurpose: String?,
    val remittanceInformation: String?,
)

data class SwiftSchemeSubmitAck(
    val endToEndId: UUID,
    val paymentId: UUID,
    val status: String = "ACCEPTED",
)
