package com.malai.standingorder.repository

import com.malai.standingorder.entity.StandingOrderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface StandingOrdersRepository : JpaRepository<StandingOrderEntity, UUID> {

    /**
     * Due-scan for the per-minute executor: ACTIVE orders whose next payment moment has
     * arrived by [asOf]. Dueness is read from the injected time service, never the wall
     * clock. CANCELLED rows (and orders with no scheduled next payment) are never returned.
     */
    @Query(
        value = """
            SELECT so.*
              FROM standing_orders so
             WHERE so.status = 'ACTIVE'
               AND so.next_payment_at IS NOT NULL
               AND so.next_payment_at <= :asOf
             ORDER BY so.next_payment_at
        """,
        nativeQuery = true,
    )
    fun findDueActiveOrders(@Param("asOf") asOf: Instant): List<StandingOrderEntity>

    @Query(
        value = """
            SELECT a.account_holder_id
              FROM accounts a
             WHERE a.account_id = :accountId
        """,
        nativeQuery = true,
    )
    fun findAccountHolderId(@Param("accountId") accountId: UUID): UUID?

    /**
     * Per-account read for the balance forecast: the account's ACTIVE standing orders only.
     * Cancelled rows never contribute to a forecast. Keyed on `account_id` directly (no
     * `accounts` join) — ownership is verified upstream in account-service before this is called.
     */
    @Query(
        value = """
            SELECT so.*
              FROM standing_orders so
             WHERE so.account_id = :accountId
               AND so.status = 'ACTIVE'
             ORDER BY so.next_payment_at NULLS LAST, so.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findActiveByAccountId(@Param("accountId") accountId: UUID): List<StandingOrderEntity>

    /**
     * Ownership filter via shared SQL (joins to `accounts`). Same approach as
     * `DirectDebitsRepository.findAllByAccountHolderId` — Plan 02-08 ArchUnit blocks
     * Kotlin-level imports of `AccountEntity`, but cross-table SQL joins are
     * allowed (modular monolith with shared schema, malai-sql).
     */
    @Query(
        value = """
            SELECT so.*
              FROM standing_orders so
              JOIN accounts a ON a.account_id = so.account_id
             WHERE a.account_holder_id = :accountHolderId
             ORDER BY so.next_payment_at NULLS LAST, so.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findAllByAccountHolderId(@Param("accountHolderId") accountHolderId: UUID): List<StandingOrderEntity>

    @Query(
        value = """
            SELECT so.*
              FROM standing_orders so
              JOIN accounts a ON a.account_id = so.account_id
             WHERE so.standing_order_id = :standingOrderId
               AND a.account_holder_id = :accountHolderId
        """,
        nativeQuery = true,
    )
    fun findByStandingOrderIdAndAccountHolderId(
        @Param("standingOrderId") standingOrderId: UUID,
        @Param("accountHolderId") accountHolderId: UUID,
    ): StandingOrderEntity?

    @Query(
        value = """
            SELECT p.name AS "name", p.sort_code AS "sortCode", p.account_number AS "accountNumber"
              FROM payees p
             WHERE p.payee_id = :payeeId
               AND p.account_holder_id = :accountHolderId
        """,
        nativeQuery = true,
    )
    fun findPayeeSnapshot(
        @Param("payeeId") payeeId: UUID,
        @Param("accountHolderId") accountHolderId: UUID,
    ): PayeeSnapshot?

    @Query(
        value = """
            SELECT EXISTS(
                SELECT 1
                  FROM accounts a
                 WHERE a.account_id = :accountId
                   AND a.account_holder_id = :accountHolderId
            )
        """,
        nativeQuery = true,
    )
    fun accountBelongsToHolder(
        @Param("accountId") accountId: UUID,
        @Param("accountHolderId") accountHolderId: UUID,
    ): Boolean
}

interface PayeeSnapshot {
    val name: String
    val sortCode: String
    val accountNumber: String
}
