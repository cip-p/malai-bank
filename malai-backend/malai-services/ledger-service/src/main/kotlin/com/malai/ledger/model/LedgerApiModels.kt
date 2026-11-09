package com.malai.ledger.model

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class RegisterLedgerAccountRequest(
    @field:NotNull val accountId: UUID?,
    @field:NotNull val currency: String?,
    @field:NotNull val kind: LedgerAccountKind?,
    @field:NotNull val guardPolicy: LedgerGuardPolicy?,
    val floorMinorUnits: Long? = null,
)

data class RegisterLedgerAccountResponse(
    val accountId: UUID,
    val currency: String,
    val kind: LedgerAccountKind,
    val guardPolicy: LedgerGuardPolicy,
    val floorMinorUnits: Long? = null,
)

data class LedgerEntryRequest(
    @field:NotNull val accountId: UUID?,
    @field:NotNull val amountMinorUnits: Long?,
    val counterpartyId: UUID? = null,
    val description: String? = null,
    val merchantName: String? = null,
    val category: String? = null,
)

data class PostTransactionRequest(
    @field:NotNull val transactionId: UUID?,
    @field:NotNull val type: String?,
    @field:NotNull val initiatedById: UUID?,
    @field:NotNull val postedAt: String?,
    val description: String? = null,
    val traceId: String? = null,
    @field:NotEmpty @field:Valid val entries: List<LedgerEntryRequest> = emptyList(),
)

data class SettleHoldRequest(
    @field:NotNull val transactionId: UUID?,
    @field:NotNull val type: String?,
    @field:NotNull val initiatedById: UUID?,
    @field:NotNull val postedAt: String?,
    val description: String? = null,
    val traceId: String? = null,
    val amountMinorUnits: Long? = null,
    @field:NotEmpty @field:Valid val entries: List<LedgerEntryRequest> = emptyList(),
) {
    fun toPosting() = PostTransactionRequest(
        transactionId = transactionId,
        type = type,
        initiatedById = initiatedById,
        postedAt = postedAt,
        description = description,
        traceId = traceId,
        entries = entries,
    )
}

data class PostedLedgerEntryResponse(
    val entryId: UUID,
    val accountId: UUID,
    val amountMinorUnits: Long,
)

data class PostTransactionResponse(
    val transactionId: UUID,
    val postedAt: String,
    val entries: List<PostedLedgerEntryResponse>,
)
