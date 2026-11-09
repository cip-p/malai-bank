package com.malai.standingorder.entity

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "standing_orders")
data class StandingOrderEntity(

    @Id
    @Column(name = "standing_order_id")
    val standingOrderId: UUID,

    @Column(name = "account_id")
    val accountId: UUID,

    /** Nullable: a new external payee may not yet appear in the saved-payees table. */
    @Column(name = "payee_id")
    val payeeId: UUID? = null,

    @Column(name = "payee_name")
    val payeeName: String,

    @Column(name = "payee_sort_code")
    val payeeSortCode: String,

    @Column(name = "payee_account_number")
    val payeeAccountNumber: String,

    val reference: String? = null,

    @Column(name = "amount_minor_units")
    val amountMinorUnits: Long,

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

    @Column(name = "next_payment_at")
    val nextPaymentAt: Instant? = null,

    @Column(name = "created_at")
    val createdAt: Instant,
)
