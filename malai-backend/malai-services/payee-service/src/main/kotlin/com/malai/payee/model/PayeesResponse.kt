package com.malai.payee.model

import java.time.Instant
import java.util.UUID

data class PayeesResponse(
    val payees: List<PayeeResponse>,
)

data class PayeeResponse(
    val payeeId: UUID,
    val name: String,
    val sortCode: String,
    val accountNumber: String,
    val nickname: String?,
    val createdAt: Instant,
)
