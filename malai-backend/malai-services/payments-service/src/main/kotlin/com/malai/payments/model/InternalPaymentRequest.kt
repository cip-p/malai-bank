package com.malai.payments.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class InternalPaymentRequest(
    @field:NotNull
    val endToEndId: UUID?,

    @field:NotNull
    val fromAccountId: UUID?,

    @field:NotNull
    val accountHolderId: UUID?,

    @field:Pattern(regexp = "\\d{2}-\\d{2}-\\d{2}", message = "sort code must match XX-XX-XX")
    val toSortCode: String? = null,

    @field:Pattern(regexp = "\\d{8}", message = "account number must be 8 digits")
    val toAccountNumber: String? = null,

    val toName: String? = null,

    @field:Min(1, message = "amountMinorUnits must be positive")
    val amountMinorUnits: Long,

    @field:NotBlank
    val currency: String = "GBP",

    val reference: String? = null,
)
