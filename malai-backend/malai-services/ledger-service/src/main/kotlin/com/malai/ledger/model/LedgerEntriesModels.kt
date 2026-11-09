package com.malai.ledger.model

import java.time.Instant
import java.util.UUID

/**
 * One posted entry with the running balance of the account it landed on. [accountId] is populated
 * only on the transaction-scoped read, where the legs of a double entry sit side by side and the
 * account is what tells them apart; on the account-scoped read the account is a property of the
 * request and rides on [EntriesResponse] instead.
 */
data class LedgerEntryProjection(
    val entryId: UUID,
    val transactionId: UUID,
    val postedAt: Instant,
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

/**
 * Every entry of one transaction, under the transaction's own facts. The entries are the legs of a
 * single double entry, ordered debit-first — the same order the posting engine returns on an
 * idempotent replay.
 */
data class TransactionEntriesResponse(
    val transactionId: UUID,
    val postedAt: Instant,
    val type: String,
    val description: String? = null,
    val entries: List<LedgerEntryProjection>,
)

data class TracedTransactionsResponse(
    val traceId: String,
    val transactions: List<TransactionEntriesResponse>,
)
