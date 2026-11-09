package com.malai.standingorder.model

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import java.time.LocalDate
import java.util.UUID

data class InternalStandingOrdersResponse(
    val standingOrders: List<InternalStandingOrderResponse>,
)

data class InternalStandingOrderResponse(
    val standingOrderId: UUID,
    val accountId: UUID,
    val payeeName: String,
    val amountMinorUnits: Long,
    val currency: Currency,
    val frequency: Frequency,
    val status: InstrumentStatus,
    val startDate: LocalDate,
)
