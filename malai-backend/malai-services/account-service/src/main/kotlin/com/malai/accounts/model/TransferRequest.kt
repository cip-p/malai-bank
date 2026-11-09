package com.malai.accounts.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Request body for `POST /api/v1/accounts/{accountId}/transfers` (Plan 02-12, BACK-03).
 *
 * `toSortCode` + `toAccountNumber` resolve the destination account; v1 covers
 * own-to-own and own-to-EXTERNAL-clearing-account flows. External pay-someone
 * via saved payee lands with Plan 02-13 (CoP) + Plan 02-14 (payees).
 *
 * Currency is hard-locked to `GBP` in v1 — stored on the field for clarity but
 * the service will reject anything else; multi-currency is a v2 candidate.
 */
data class TransferRequest(
    @field:Pattern(regexp = "\\d{2}-\\d{2}-\\d{2}", message = "sort code must match XX-XX-XX")
    val toSortCode: String,
    @field:Pattern(regexp = "\\d{8}", message = "account number must be 8 digits")
    val toAccountNumber: String,
    @field:Min(1, message = "amountMinorUnits must be positive")
    val amountMinorUnits: Long,
    @field:NotBlank
    val currency: String = "GBP",
    val description: String? = null,
)
