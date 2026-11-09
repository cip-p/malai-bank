package com.malai.directdebit.entity

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "direct_debits")
data class DirectDebitEntity(

    @Id
    @Column(name = "direct_debit_id")
    val directDebitId: UUID,

    @Column(name = "account_id")
    val accountId: UUID,

    @Column(name = "creditor_name")
    val creditorName: String,

    @Column(name = "creditor_sort_code")
    val creditorSortCode: String,

    @Column(name = "creditor_account_number")
    val creditorAccountNumber: String,

    val reference: String? = null,

    @Column(name = "amount_minor_units")
    val amountMinorUnits: Long? = null,

    @Enumerated(STRING)
    val currency: Currency,

    @Enumerated(STRING)
    val frequency: Frequency,

    @Enumerated(STRING)
    val status: InstrumentStatus,

    @Column(name = "start_date")
    val startDate: LocalDate,

    @Column(name = "cancelled_at")
    val cancelledAt: Instant? = null,

    @Column(name = "next_collection_at")
    val nextCollectionAt: Instant? = null,

    @Column(name = "created_at")
    val createdAt: Instant,
)
