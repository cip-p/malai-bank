package com.malai.standingorder.model

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class StandingOrdersResponse(
    val standingOrders: List<StandingOrderResponse>,
)

data class StandingOrderResponse(
    val standingOrderId: UUID,
    val accountId: UUID,
    val payeeId: UUID?,
    val payeeName: String,
    val payeeSortCode: String,
    val payeeAccountNumber: String,
    val reference: String?,
    val amountMinorUnits: Long,
    val currency: Currency,
    val frequency: Frequency,
    val status: InstrumentStatus,
    val startDate: LocalDate,
    val cancelledAt: Instant?,
    val nextPaymentAt: Instant?,
)
