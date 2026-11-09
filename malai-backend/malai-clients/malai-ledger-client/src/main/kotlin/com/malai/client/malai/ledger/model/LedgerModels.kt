package com.malai.client.malai.ledger.model

import java.util.UUID

enum class LedgerAccountKind { CUSTOMER, SYSTEM }

enum class LedgerGuardPolicy { NO_OVERDRAFT, ALLOW_NEGATIVE, FLOORED }

data class RegisterLedgerAccountRequest(
    val accountId: UUID,
    val currency: String,
    val kind: LedgerAccountKind,
    val guardPolicy: LedgerGuardPolicy,
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
    val accountId: UUID,
    val amountMinorUnits: Long,
    val counterpartyId: UUID? = null,
    val description: String? = null,
    val merchantName: String? = null,
    val category: String? = null,
)

data class PostTransactionRequest(
    val transactionId: UUID,
    val type: String,
    val initiatedById: UUID,
    val postedAt: String,
    val description: String? = null,
    val traceId: String? = null,
    val entries: List<LedgerEntryRequest>,
)

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

enum class HoldStatus { PLACED, SETTLED, RELEASED }

data class PlaceHoldRequest(
    val holdId: UUID,
    val accountId: UUID,
    val amountMinorUnits: Long,
    val placedAt: String,
)

data class ReleaseHoldRequest(
    val resolvedAt: String,
)

data class HoldResponse(
    val holdId: UUID,
    val accountId: UUID,
    val amountMinorUnits: Long,
    val status: HoldStatus,
    val placedAt: String,
    val resolvedAt: String? = null,
)

data class SettleHoldRequest(
    val transactionId: UUID,
    val type: String,
    val initiatedById: UUID,
    val postedAt: String,
    val description: String? = null,
    val traceId: String? = null,
    val amountMinorUnits: Long? = null,
    val entries: List<LedgerEntryRequest>,
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

data class LedgerEntryProjection(
    val entryId: UUID,
    val transactionId: UUID,
    val postedAt: String,
    val amountMinorUnits: Long,
    val runningBalanceMinorUnits: Long,
    val description: String? = null,
    val counterpartyId: UUID? = null,
    val merchantName: String? = null,
    val category: String? = null,
    val transactionType: String? = null,
    val accountId: UUID? = null,
)

data class EntriesResponse(
    val accountId: UUID,
    val entries: List<LedgerEntryProjection>,
    val openingBalanceMinorUnits: Long? = null,
    val closingBalanceMinorUnits: Long? = null,
)

data class TransactionEntriesResponse(
    val transactionId: UUID,
    val postedAt: String,
    val type: String,
    val description: String? = null,
    val entries: List<LedgerEntryProjection>,
)

data class TracedTransactionsResponse(
    val traceId: String,
    val transactions: List<TransactionEntriesResponse>,
)
