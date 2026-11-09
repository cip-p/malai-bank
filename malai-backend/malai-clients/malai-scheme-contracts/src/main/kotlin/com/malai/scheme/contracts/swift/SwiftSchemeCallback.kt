package com.malai.scheme.contracts.swift

import java.util.UUID

data class SwiftSchemeSettlementCallback(
    val endToEndId: UUID,
    val paymentId: UUID,
)
