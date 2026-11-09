package com.malai.ledger.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "transactions")
data class LedgerTransactionEntity(

    @Id
    @Column(name = "transaction_id", nullable = false)
    val transactionId: UUID,

    @Column(name = "initiated_by_id", nullable = false)
    val initiatedById: UUID,

    @Column(name = "initiated_at", nullable = false)
    val initiatedAt: Instant,

    @Column(name = "type", nullable = false, length = 30)
    val type: String,

    @Column(name = "description", length = 500)
    val description: String? = null,

    @Column(name = "trace_id", length = 64)
    val traceId: String? = null,
)
