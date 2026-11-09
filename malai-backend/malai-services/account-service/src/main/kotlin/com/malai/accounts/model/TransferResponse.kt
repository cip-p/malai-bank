package com.malai.accounts.model

import com.malai.commons.model.Currency
import java.time.Instant
import java.util.UUID

/**
 * Response for `POST /api/v1/accounts/{accountId}/transfers` (Plan 02-12).
 * `amountMinorUnits` is the positive value debited from `fromAccountId` and
 * credited to `toAccountId` — the signed ledger entries (-X / +X) live in
 * `ledger_entries` and are the source of truth for balance projection.
 */
data class TransferResponse(
    val transactionId: UUID,
    val postedAt: Instant,
    val fromAccountId: UUID,
    val toAccountId: UUID,
    val amountMinorUnits: Long,
    val currency: Currency,
)
