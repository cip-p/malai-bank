package com.malai.ledger.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ledger_entries")
data class LedgerEntryRowEntity(

    @Id
    @Column(name = "entry_id", nullable = false)
    val entryId: UUID,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: UUID,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "amount_minor_units", nullable = false)
    val amountMinorUnits: Long,

    @Column(name = "posted_at", nullable = false)
    val postedAt: Instant,

    @Column(name = "description", length = 500)
    val description: String? = null,

    @Column(name = "counterparty_id")
    val counterpartyId: UUID? = null,

    @Column(name = "merchant_name", length = 255)
    val merchantName: String? = null,

    @Column(name = "category", length = 50)
    val category: String? = null,

    @Column(name = "trace_id", length = 64)
    val traceId: String? = null,
)
