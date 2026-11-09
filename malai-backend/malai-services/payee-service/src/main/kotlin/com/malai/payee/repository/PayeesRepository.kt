package com.malai.payee.repository

import com.malai.payee.entity.PayeeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PayeesRepository : JpaRepository<PayeeEntity, UUID> {

    fun findAllByAccountHolderIdOrderByCreatedAtDesc(accountHolderId: UUID): List<PayeeEntity>

    fun findByPayeeIdAndAccountHolderId(payeeId: UUID, accountHolderId: UUID): PayeeEntity?

    /**
     * Deletion guard reaching into `standing_orders` via shared SQL — the same modular-monolith
     * exception `StandingOrdersRepository.findPayeeSnapshot` relies on to read `payees`. A payee
     * referenced by an ACTIVE order must not be deleted (it would strand a live instruction);
     * CANCELLED orders never match, so the FK's ON DELETE SET NULL clears their link on delete.
     */
    @Query(
        value = """
            SELECT EXISTS(
                SELECT 1
                  FROM standing_orders so
                 WHERE so.payee_id = :payeeId
                   AND so.status = 'ACTIVE'
            )
        """,
        nativeQuery = true,
    )
    fun existsActiveStandingOrderForPayee(@Param("payeeId") payeeId: UUID): Boolean
}
