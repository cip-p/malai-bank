package com.malai.payments.repository

import com.malai.commons.model.Currency
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PaymentRepository : JpaRepository<PaymentEntity, UUID> {

    fun findByStateOrderByCreatedAtAsc(state: PaymentState): List<PaymentEntity>
    fun findByPaymentIdAndCreatedByAccountHolderId(
        paymentId: UUID,
        createdByAccountHolderId: UUID,
    ): PaymentEntity?

    fun findByEndToEndId(endToEndId: UUID): PaymentEntity?

    @Query(
        """
        SELECT p
        FROM PaymentEntity p
        WHERE p.state = com.malai.payments.entity.PaymentState.SETTLED
          AND p.createdAt >= :from
          AND p.createdAt < :until
        ORDER BY p.createdAt ASC
        """,
    )
    fun findSettledInWindow(
        @Param("from") from: Instant,
        @Param("until") until: Instant,
    ): List<PaymentEntity>

    @Query(
        """
        SELECT COALESCE(SUM(p.amountMinorUnits), 0)
        FROM PaymentEntity p
        WHERE p.direction = com.malai.payments.entity.PaymentDirection.INBOUND
          AND p.state = com.malai.payments.entity.PaymentState.SETTLED
          AND p.beneficiaryAccountId IS NOT NULL
          AND p.currency = :currency
          AND p.createdAt >= :from
          AND p.createdAt < :until
        """,
    )
    fun sumSettledInboundDeposits(
        @Param("currency") currency: Currency,
        @Param("from") from: Instant,
        @Param("until") until: Instant,
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(p.amountMinorUnits), 0)
        FROM PaymentEntity p
        WHERE p.state = com.malai.payments.entity.PaymentState.DECLINED
          AND p.currency = :currency
          AND p.createdAt >= :from
          AND p.createdAt < :until
        """,
    )
    fun sumDeclinedPayments(
        @Param("currency") currency: Currency,
        @Param("from") from: Instant,
        @Param("until") until: Instant,
    ): Long
}
