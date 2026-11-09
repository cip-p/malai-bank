package com.malai.standingorder.model

import com.malai.commons.model.Frequency
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

/**
 * Request body for `POST /api/v1/standing-orders` (SO/DD lifecycle issue 03).
 *
 * `payeeId` is required — creation is only possible against a saved payee, whose
 * details are snapshotted onto the order. `startDate` is the immutable schedule
 * anchor (today or later — the semantic guard lives in the service, where "today"
 * comes from the injected time service). `currency` is GBP-guarded in the service.
 */
data class StandingOrderRequest(
    @field:NotNull
    val accountId: UUID?,

    @field:NotNull
    val payeeId: UUID?,

    @field:Min(1, message = "amountMinorUnits must be positive")
    val amountMinorUnits: Long,

    @field:NotBlank
    val currency: String = "GBP",

    @field:Size(max = 80, message = "reference must be at most 80 characters")
    val reference: String? = null,

    @field:NotNull
    val frequency: Frequency?,

    @field:NotNull
    val startDate: LocalDate?,
)
