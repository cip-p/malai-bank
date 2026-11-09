package com.malai.accounts.model

import com.malai.commons.model.Currency
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Response for `GET /api/v1/accounts/{accountUid}/statements/{yyyymm}` (Plan 02-18).
 *
 * `periodStart` is the first instant of the requested calendar month in UTC;
 * `periodEnd` is the first instant of the following month (exclusive upper
 * bound). `openingBalanceMinorUnits` is the signed balance just before
 * `periodStart`; `closingBalanceMinorUnits` is the balance just before
 * `periodEnd` (equivalently, the running balance after the last entry in
 * the period, or the opening balance when the month has zero entries).
 *
 * Running balance on each transaction is computed across the account's
 * full history (window function partitioned by account_id), not just over
 * the month's entries — so the oldest entry of the month carries the
 * correct cumulative balance.
 *
 * `statementBalanceMinorUnits`, `minimumPaymentMinorUnits`, and `dueDate`
 * are the credit-card statement figures — present only for a `CREDIT_CARD`
 * account, and derived by `card-service` from the closing position and the
 * cycle month. They are absent (omitted) for every other account type.
 */
data class StatementResponse(
    val accountUid: UUID,
    val periodStart: Instant,
    val periodEnd: Instant,
    val currency: Currency,
    val openingBalanceMinorUnits: Long,
    val closingBalanceMinorUnits: Long,
    val transactions: List<TransactionItemResponse>,
    val statementBalanceMinorUnits: Long? = null,
    val minimumPaymentMinorUnits: Long? = null,
    val dueDate: LocalDate? = null,
)
