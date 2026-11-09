package com.malai.accounts.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ConfirmationOfPayeeRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 140, message = "name must be at most 140 characters")
    val name: String,

    @field:Pattern(regexp = "\\d{2}-\\d{2}-\\d{2}", message = "sort code must match XX-XX-XX")
    val sortCode: String,

    @field:Pattern(regexp = "\\d{8}", message = "account number must be 8 digits")
    val accountNumber: String,
)
