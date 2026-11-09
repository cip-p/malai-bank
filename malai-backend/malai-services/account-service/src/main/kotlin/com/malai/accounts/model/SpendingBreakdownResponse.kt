package com.malai.accounts.model

import com.malai.commons.model.Currency

/**
 * Response for `GET /api/v1/accounts/{accountUid}/spending?month=YYYY-MM`
 * (spending-insights issue 02). account-service reads the month's ledger
 * entries and delegates the per-`Category` aggregation to `insights-service`,
 * then wraps the result with the account's `currency` and the requested
 * `month`.
 *
 * Every `totalMinorUnits` is a positive spend magnitude (debits only);
 * `INCOME` and `TRANSFERS` never appear. Unlike the transaction feed, this
 * endpoint fails closed — a `503` when insights is unavailable rather than a
 * degraded read — because the breakdown *is* the insights screen.
 */
data class SpendingBreakdownResponse(
    val month: String,
    val currency: Currency,
    val categories: List<SpendingCategoryTotalResponse>,
    val totalMinorUnits: Long,
)

data class SpendingCategoryTotalResponse(
    val category: String,
    val totalMinorUnits: Long,
    val transactionCount: Int,
)
