package com.malai.card.entity

import com.malai.commons.model.Currency
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "card_refunds")
data class CardRefundEntity(

    @Id
    @Column(name = "refund_reference", nullable = false)
    val refundReference: UUID,

    @Column(name = "authorisation_id", nullable = false)
    val authorisationId: UUID,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "scheme_reference", nullable = false)
    val schemeReference: UUID,

    @Column(name = "merchant_name", nullable = false, length = 255)
    val merchantName: String,

    @Column(name = "amount_minor_units", nullable = false)
    val amountMinorUnits: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    val currency: Currency,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
