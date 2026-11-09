package com.malai.accounts.model

import com.malai.commons.model.Currency

/**
 * Composed from the ledger's three balances ([com.malai.client.malai.ledger.LedgerClient.getBalances]).
 *
 * - [amountMinorUnits] is the **Ledger balance** — `SUM(entries)`, the book of record. It no longer
 *   drops when an outbound payment is in-flight; it moves only when money actually settles.
 * - [availableBalanceMinorUnits] is **Available** — `ledger - held` — and is what the webapp should
 *   lead with, since an in-flight (PLACED hold) payment drops Available immediately.
 * - [heldAmountMinorUnits] is the total of active holds reserving funds.
 *
 * [pendingMinorUnits] is reserved for a future pending lane; always `null` in v1 (D-2.8 keeps v1
 * single-lifecycle).
 */
data class BalanceResponse(
    val currency: Currency,
    val amountMinorUnits: Long,
    val availableBalanceMinorUnits: Long,
    val heldAmountMinorUnits: Long,
    val pendingMinorUnits: Long? = null,
)
