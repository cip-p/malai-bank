package com.malai.ledger.entity

import com.malai.ledger.model.HoldStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "holds")
data class HoldEntity(

    @Id
    @Column(name = "hold_id", nullable = false)
    val holdId: UUID,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "amount_minor_units", nullable = false)
    val amountMinorUnits: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: HoldStatus,

    @Column(name = "placed_at", nullable = false)
    val placedAt: Instant,

    @Column(name = "resolved_at")
    val resolvedAt: Instant? = null,
)
