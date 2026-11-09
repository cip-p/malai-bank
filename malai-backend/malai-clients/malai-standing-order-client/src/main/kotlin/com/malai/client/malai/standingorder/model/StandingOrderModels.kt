package com.malai.client.malai.standingorder.model

import java.util.UUID

data class ActiveStandingOrdersResponse(
    val standingOrders: List<ActiveStandingOrder>,
)

data class ActiveStandingOrder(
    val standingOrderId: UUID,
    val accountId: UUID,
    val payeeName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val frequency: String,
    val status: String,
    val startDate: String,
)
