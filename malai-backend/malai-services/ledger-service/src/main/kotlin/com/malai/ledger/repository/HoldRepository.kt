package com.malai.ledger.repository

import com.malai.ledger.entity.HoldEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface HoldRepository : JpaRepository<HoldEntity, UUID> {

    fun findByAccountId(accountId: UUID): List<HoldEntity>

    /**
     * The account's Held amount — the `SUM(amount_minor_units)` over its still-active
     * (`PLACED`) holds. Returns `0` when the account has no active holds. The funds guard
     * subtracts this from the Ledger balance to get the Available balance a posting or a
     * new hold must not breach.
     */
    @Query(
        """
        SELECT COALESCE(SUM(h.amountMinorUnits), 0)
          FROM HoldEntity h
         WHERE h.accountId = :accountId
           AND h.status = com.malai.ledger.model.HoldStatus.PLACED
        """
    )
    fun heldAmountOf(@Param("accountId") accountId: UUID): Long
}
