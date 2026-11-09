package com.malai.accounts.model

import com.malai.commons.model.Currency
import java.util.UUID

data class BalanceForecastResponse(
    val accountUid: UUID,
    val month: String,
    val windowStart: String,
    val windowEnd: String,
    val asOfDate: String,
    val currency: Currency,
    val availableBalanceMinorUnits: Long,
    val completedTotalMinorUnits: Long,
    val expectedTotalMinorUnits: Long,
    val forecastBalanceMinorUnits: Long,
    val completed: List<ForecastOccurrenceResponse>,
    val expected: List<ForecastOccurrenceResponse>,
)

data class ForecastOccurrenceResponse(
    val date: String,
    val counterpartyName: String,
    val amountMinorUnits: Long,
    val type: String,
    val instrumentId: UUID,
)
