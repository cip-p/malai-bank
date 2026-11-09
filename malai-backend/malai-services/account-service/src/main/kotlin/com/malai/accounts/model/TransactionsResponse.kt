package com.malai.accounts.model

import com.malai.commons.model.Currency
import java.time.Instant
import java.util.UUID

/**
 * Response for `GET /api/v1/accounts/{accountUid}/transactions` (Plan 02-17).
 *
 * Hard-capped at 100 newest entries; running balance is computed in Postgres
 * via a `SUM(amount_minor_units) OVER (PARTITION BY account_id ORDER BY
 * posted_at ASC, entry_id ASC)` window so the oldest visible row still
 * reflects everything that came before it. Flat money shape mirrors
 * [BalanceResponse] — `currency` is single-valued for the account, so amount
 * and running balance share it instead of being nested Money objects.
 */
data class TransactionsResponse(
    val accountUid: UUID,
    val transactions: List<TransactionItemResponse>,
)

/**
 * `PENDING` / `DECLINED` mark a card-authorisation row composed into the feed by
 * [com.malai.accounts.service.AccountsService.getTransactions]; a plain settled ledger row
 * carries no status (it keeps today's shape). See ADR-0006.
 */
enum class TransactionStatus { PENDING, DECLINED }

data class TransactionItemResponse(
    val entryId: UUID,
    val transactionId: UUID,
    val postedAt: Instant,
    val currency: Currency,
    val amountMinorUnits: Long,
    val runningBalanceMinorUnits: Long,
    val description: String? = null,
    val counterpartyId: UUID? = null,
    val merchantName: String? = null,
    val category: String? = null,
    val status: TransactionStatus? = null,
    val declineReason: String? = null,
    val cardId: UUID? = null,
)
