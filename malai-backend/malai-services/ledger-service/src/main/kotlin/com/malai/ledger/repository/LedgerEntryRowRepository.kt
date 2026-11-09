package com.malai.ledger.repository

import com.malai.ledger.entity.LedgerEntryRowEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface LedgerEntryRowRepository : JpaRepository<LedgerEntryRowEntity, UUID> {

    fun findByTransactionId(transactionId: UUID): List<LedgerEntryRowEntity>

    fun findByTransactionIdOrderByAmountMinorUnitsAsc(transactionId: UUID): List<LedgerEntryRowEntity>

    /**
     * The account's Ledger balance — the signed `SUM(amount_minor_units)` over all its entries.
     * Returns `0` when the account has no entries. While no holds exist (issue 01) this is also
     * the Available balance the funds guard checks.
     */
    @Query(
        """
        SELECT COALESCE(SUM(e.amountMinorUnits), 0)
          FROM LedgerEntryRowEntity e
         WHERE e.accountId = :accountId
        """
    )
    fun ledgerBalanceOf(@Param("accountId") accountId: UUID): Long

    /**
     * The account's Ledger balance projected as of a point strictly before [asOf]. Used by the
     * statement projection for the opening balance (entries before the period start) and the
     * closing balance (entries before the period end, exclusive). Returns `0` when no entries
     * predate [asOf].
     */
    @Query(
        """
        SELECT COALESCE(SUM(e.amountMinorUnits), 0)
          FROM LedgerEntryRowEntity e
         WHERE e.accountId = :accountId
           AND e.postedAt < :asOf
        """
    )
    fun balanceBefore(@Param("accountId") accountId: UUID, @Param("asOf") asOf: Instant): Long

    /**
     * The most recent [limit] entries on [accountId], newest-first, each carrying the running
     * balance at that point in time. The running balance is computed by Postgres in a single
     * native query using a window function over the account's full history; the outer
     * `ORDER BY ... DESC LIMIT :limit` then picks the newest rows — so the oldest visible row's
     * `runningBalanceMinorUnits` still includes every older entry the cap clipped out.
     *
     * Deterministic tiebreaker on `entry_id` keeps ordering stable when multiple entries share an
     * exact `posted_at` (seeder, batched transfers).
     */
    @Query(
        value = """
            SELECT e.entry_id            AS entryId,
                   e.account_id          AS accountId,
                   e.transaction_id      AS transactionId,
                   e.posted_at           AS postedAt,
                   e.amount_minor_units  AS amountMinorUnits,
                   e.description         AS description,
                   e.counterparty_id     AS counterpartyId,
                   e.merchant_name       AS merchantName,
                   e.category            AS category,
                   t.type                AS transactionType,
                   SUM(e.amount_minor_units) OVER (
                     PARTITION BY e.account_id
                     ORDER BY e.posted_at ASC, e.entry_id ASC
                     ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                   )                   AS runningBalanceMinorUnits
              FROM ledger_entries e
              JOIN transactions t ON t.transaction_id = e.transaction_id
             WHERE e.account_id = :accountId
             ORDER BY e.posted_at DESC, e.entry_id DESC
             LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecentEntriesForAccount(
        @Param("accountId") accountId: UUID,
        @Param("limit") limit: Int,
    ): List<LedgerEntryProjectionRow>

    /**
     * Entries on [accountId] with `postedAt` in `[periodStart, periodEnd)`, newest-first, each
     * carrying the running balance at that point in time computed across the account's **full**
     * history (not just over the period). The window function runs in an inner subquery so the
     * period filter does not corrupt the cumulative sum — the oldest entry of the period still
     * includes every prior entry the WHERE clause clipped out.
     *
     * No hard cap — a calendar month is the natural bound.
     */
    @Query(
        value = """
            SELECT entryId, accountId, transactionId, postedAt, amountMinorUnits,
                   description, counterpartyId, merchantName, category, transactionType,
                   runningBalanceMinorUnits
              FROM (
                SELECT e.entry_id            AS entryId,
                       e.account_id          AS accountId,
                       e.transaction_id      AS transactionId,
                       e.posted_at           AS postedAt,
                       e.amount_minor_units  AS amountMinorUnits,
                       e.description         AS description,
                       e.counterparty_id     AS counterpartyId,
                       e.merchant_name       AS merchantName,
                       e.category            AS category,
                       t.type                AS transactionType,
                       SUM(e.amount_minor_units) OVER (
                         PARTITION BY e.account_id
                         ORDER BY e.posted_at ASC, e.entry_id ASC
                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       )                   AS runningBalanceMinorUnits
                  FROM ledger_entries e
                  JOIN transactions t ON t.transaction_id = e.transaction_id
                 WHERE e.account_id = :accountId
              ) windowed
             WHERE postedAt >= :periodStart
               AND postedAt <  :periodEnd
             ORDER BY postedAt DESC, entryId DESC
        """,
        nativeQuery = true,
    )
    fun findEntriesForAccountInPeriod(
        @Param("accountId") accountId: UUID,
        @Param("periodStart") periodStart: Instant,
        @Param("periodEnd") periodEnd: Instant,
    ): List<LedgerEntryProjectionRow>

    /**
     * Every entry of one transaction — the legs of a single double entry — each carrying the
     * running balance of its own account at that point in time. The window function runs in an
     * inner subquery scoped to the accounts the transaction touches, so each leg's cumulative sum
     * includes that account's full prior history while the outer filter clips to this transaction.
     *
     * Ordered by amount ascending, so the debit leg precedes the credit leg — the same order
     * [findByTransactionIdOrderByAmountMinorUnitsAsc] gives the posting engine on an idempotent
     * replay. `entryId` breaks ties when a transaction posts two legs of equal amount.
     */
    @Query(
        value = """
            SELECT entryId, accountId, transactionId, postedAt, amountMinorUnits,
                   description, counterpartyId, merchantName, category, transactionType,
                   runningBalanceMinorUnits
              FROM (
                SELECT e.entry_id            AS entryId,
                       e.account_id          AS accountId,
                       e.transaction_id      AS transactionId,
                       e.posted_at           AS postedAt,
                       e.amount_minor_units  AS amountMinorUnits,
                       e.description         AS description,
                       e.counterparty_id     AS counterpartyId,
                       e.merchant_name       AS merchantName,
                       e.category            AS category,
                       t.type                AS transactionType,
                       SUM(e.amount_minor_units) OVER (
                         PARTITION BY e.account_id
                         ORDER BY e.posted_at ASC, e.entry_id ASC
                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       )                   AS runningBalanceMinorUnits
                  FROM ledger_entries e
                  JOIN transactions t ON t.transaction_id = e.transaction_id
                 WHERE e.account_id IN (
                         SELECT account_id FROM ledger_entries WHERE transaction_id = :transactionId
                       )
              ) windowed
             WHERE transactionId = :transactionId
             ORDER BY amountMinorUnits ASC, entryId ASC
        """,
        nativeQuery = true,
    )
    fun findEntriesForTransaction(
        @Param("transactionId") transactionId: UUID,
    ): List<LedgerEntryProjectionRow>

    @Query(
        value = """
            SELECT entryId, accountId, transactionId, postedAt, amountMinorUnits,
                   description, counterpartyId, merchantName, category, transactionType,
                   runningBalanceMinorUnits
              FROM (
                SELECT e.entry_id            AS entryId,
                       e.account_id          AS accountId,
                       e.transaction_id      AS transactionId,
                       e.posted_at           AS postedAt,
                       e.amount_minor_units  AS amountMinorUnits,
                       e.description         AS description,
                       e.counterparty_id     AS counterpartyId,
                       e.merchant_name       AS merchantName,
                       e.category            AS category,
                       t.type                AS transactionType,
                       SUM(e.amount_minor_units) OVER (
                         PARTITION BY e.account_id
                         ORDER BY e.posted_at ASC, e.entry_id ASC
                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                       )                   AS runningBalanceMinorUnits
                  FROM ledger_entries e
                  JOIN transactions t ON t.transaction_id = e.transaction_id
                 WHERE e.account_id IN (
                         SELECT le.account_id
                           FROM ledger_entries le
                           JOIN transactions lt ON lt.transaction_id = le.transaction_id
                          WHERE lt.trace_id = :traceId
                       )
              ) windowed
             WHERE transactionId IN (
                     SELECT transaction_id FROM transactions WHERE trace_id = :traceId
                   )
             ORDER BY postedAt ASC, transactionId ASC, amountMinorUnits ASC, entryId ASC
        """,
        nativeQuery = true,
    )
    fun findEntriesForTrace(
        @Param("traceId") traceId: String,
    ): List<LedgerEntryProjectionRow>
}

/**
 * Spring Data interface projection bound to the column aliases in
 * [LedgerEntryRowRepository.findRecentEntriesForAccount] /
 * [LedgerEntryRowRepository.findEntriesForAccountInPeriod].
 */
interface LedgerEntryProjectionRow {
    fun getEntryId(): UUID
    fun getAccountId(): UUID
    fun getTransactionId(): UUID
    fun getPostedAt(): Instant
    fun getAmountMinorUnits(): Long
    fun getDescription(): String?
    fun getCounterpartyId(): UUID?
    fun getMerchantName(): String?
    fun getCategory(): String?
    fun getTransactionType(): String?
    fun getRunningBalanceMinorUnits(): Long
}
