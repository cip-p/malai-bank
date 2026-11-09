package com.malai.ledger.model

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class PlaceHoldRequest(
    @field:NotNull val holdId: UUID?,
    @field:NotNull val accountId: UUID?,
    @field:NotNull val amountMinorUnits: Long?,
    @field:NotNull val placedAt: String?,
)

data class ReleaseHoldRequest(
    @field:NotNull val resolvedAt: String?,
)

data class HoldResponse(
    val holdId: UUID,
    val accountId: UUID,
    val amountMinorUnits: Long,
    val status: HoldStatus,
    val placedAt: String,
    val resolvedAt: String? = null,
)

data class SettleHoldResponse(
    val holdId: UUID,
    val status: HoldStatus,
    val resolvedAt: String,
    val transaction: PostTransactionResponse,
)

data class BalancesResponse(
    val accountId: UUID,
    val ledgerBalanceMinorUnits: Long,
    val heldAmountMinorUnits: Long,
    val availableBalanceMinorUnits: Long,
)
