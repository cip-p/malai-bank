package com.malai.payee.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for `POST /api/v1/payees` (Plan 02-14, BACK-08 saved-beneficiary half).
 *
 * Sort code matches `XX-XX-XX` and account number matches 8 digits — the standard UK
 * format the rest of the demo speaks (D-2.20). Saved payees can point at OTHER banks
 * (this isn't restricted to the MALAI sort-code prefix), so the request only validates
 * shape, not the prefix — Plan 02-13's CoP service surfaces "no_match" for unknown
 * external sort+account combinations.
 *
 * `nickname` is optional cosmetic UX (Sarah's "Mum's birthday account").
 */
data class PayeeRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 140, message = "name must be at most 140 characters")
    val name: String,

    @field:Pattern(regexp = "\\d{2}-\\d{2}-\\d{2}", message = "sort code must match XX-XX-XX")
    val sortCode: String,

    @field:Pattern(regexp = "\\d{8}", message = "account number must be 8 digits")
    val accountNumber: String,

    @field:Size(max = 80, message = "nickname must be at most 80 characters")
    val nickname: String? = null,
)
