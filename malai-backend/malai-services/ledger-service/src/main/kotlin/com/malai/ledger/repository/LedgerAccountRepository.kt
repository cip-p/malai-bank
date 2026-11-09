package com.malai.ledger.repository

import com.malai.ledger.entity.LedgerAccountEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LedgerAccountRepository : JpaRepository<LedgerAccountEntity, UUID> {

    /**
     * Locks the registry row (`SELECT ... FOR UPDATE`) so concurrent postings to the same
     * account serialise — the funds guard reads the balance and applies the posting under
     * this lock, so two concurrent debits cannot both pass the guard against the same money.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT la FROM LedgerAccountEntity la WHERE la.accountId = :accountId")
    fun lockByAccountId(@Param("accountId") accountId: UUID): LedgerAccountEntity?
}
