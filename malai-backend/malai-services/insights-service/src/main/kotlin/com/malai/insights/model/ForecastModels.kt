package com.malai.insights.model

import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import java.time.LocalDate
import java.util.UUID

data class BalanceForecastRequest(
    val asOfDate: String,
    val availableBalanceMinorUnits: Long,
    val currency: String,
    val instruments: List<ForecastInstrumentFact>,
)

data class ForecastInstrumentFact(
    val instrumentId: UUID,
    val type: String,
    val counterpartyName: String,
    val amountMinorUnits: Long,
    val frequency: String,
    val startDate: String,
    val status: String,
)

data class ForecastInstrument(
    val instrumentId: UUID,
    val type: String,
    val counterpartyName: String,
    val amountMinorUnits: Long,
    val frequency: Frequency,
    val startDate: LocalDate,
    val status: InstrumentStatus,
)

data class BalanceForecastResponse(
    val month: String,
    val windowStart: String,
    val windowEnd: String,
    val asOfDate: String,
    val currency: String,
    val availableBalanceMinorUnits: Long,
    val completedTotalMinorUnits: Long,
    val expectedTotalMinorUnits: Long,
    val forecastBalanceMinorUnits: Long,
    val completed: List<ForecastOccurrence>,
    val expected: List<ForecastOccurrence>,
)

data class ForecastOccurrence(
    val date: String,
    val counterpartyName: String,
    val amountMinorUnits: Long,
    val type: String,
    val instrumentId: UUID,
)
