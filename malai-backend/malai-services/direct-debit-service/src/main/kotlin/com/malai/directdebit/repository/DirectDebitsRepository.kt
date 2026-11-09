package com.malai.directdebit.repository

import com.malai.directdebit.entity.DirectDebitEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface DirectDebitsRepository : JpaRepository<DirectDebitEntity, UUID> {

    /**
     * Due-scan for the per-minute collection requester: ACTIVE mandates whose next collection
     * moment has arrived by [asOf]. Dueness is read from the injected time service, never the
     * wall clock. CANCELLED mandates (and mandates with no scheduled next collection) are never
     * returned. Global, not scoped to an account holder — the scheduler collects every due
     * mandate.
     */
    @Query(
        value = """
            SELECT dd.*
              FROM direct_debits dd
             WHERE dd.status = 'ACTIVE'
               AND dd.next_collection_at IS NOT NULL
               AND dd.next_collection_at <= :asOf
             ORDER BY dd.next_collection_at
        """,
        nativeQuery = true,
    )
    fun findDueActiveMandates(@Param("asOf") asOf: Instant): List<DirectDebitEntity>

    /**
     * Resolves the debtor's rail details (the customer being collected from) for a mandate's
     * account via shared SQL. The stub originates the collection carrying these, and the inbound
     * rail resolves the debtor back by the same sort-code/account-number pair — closing the round
     * trip. The Kotlin layer can't import account-service entities (Plan 02-08 ArchUnit
     * `noCrossModuleInternalAccess`), but the schema is shared via malai-sql, so the cross-table
     * join is the pragmatic v1 read — same modular-monolith pattern as `findAllByAccountHolderId`
     * and `StandingOrdersRepository.findAccountHolderId`.
     */
    @Query(
        value = """
            SELECT a.name AS "name",
                   ai.bank_identifier AS "sortCode",
                   ai.account_identifier AS "accountNumber"
              FROM accounts a
              JOIN account_identifiers ai
                ON ai.account_id = a.account_id
               AND ai.identifier_type = 'SORT_CODE'
             WHERE a.account_id = :accountId
        """,
        nativeQuery = true,
    )
    fun findDebtorSnapshot(@Param("accountId") accountId: UUID): DebtorSnapshot?

    /**
     * Per-account read for the balance forecast: the account's ACTIVE direct-debit mandates only.
     * Cancelled mandates never contribute to a forecast. Keyed on `account_id` directly (no
     * `accounts` join) — ownership is verified upstream in account-service before this is called.
     * Twin of `StandingOrdersRepository.findActiveByAccountId`.
     */
    @Query(
        value = """
            SELECT dd.*
              FROM direct_debits dd
             WHERE dd.account_id = :accountId
               AND dd.status = 'ACTIVE'
             ORDER BY dd.next_collection_at NULLS LAST, dd.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findActiveByAccountId(@Param("accountId") accountId: UUID): List<DirectDebitEntity>

    /**
     * Ownership filter via shared SQL (joins to `accounts`). The Kotlin layer can't
     * import `AccountEntity` from account-service (Plan 02-08 ArchUnit
     * `noCrossModuleInternalAccess` rule), but the schema is shared via malai-sql
     * so a native cross-table query is the pragmatic v1 join — same pattern banks
     * use inside a modular monolith.
     */
    @Query(
        value = """
            SELECT dd.*
              FROM direct_debits dd
              JOIN accounts a ON a.account_id = dd.account_id
             WHERE a.account_holder_id = :accountHolderId
             ORDER BY dd.next_collection_at NULLS LAST, dd.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findAllByAccountHolderId(@Param("accountHolderId") accountHolderId: UUID): List<DirectDebitEntity>

    @Query(
        value = """
            SELECT dd.*
              FROM direct_debits dd
              JOIN accounts a ON a.account_id = dd.account_id
             WHERE dd.direct_debit_id = :directDebitId
               AND a.account_holder_id = :accountHolderId
        """,
        nativeQuery = true,
    )
    fun findByDirectDebitIdAndAccountHolderId(
        @Param("directDebitId") directDebitId: UUID,
        @Param("accountHolderId") accountHolderId: UUID,
    ): DirectDebitEntity?
}

interface DebtorSnapshot {
    val name: String
    val sortCode: String
    val accountNumber: String
}
