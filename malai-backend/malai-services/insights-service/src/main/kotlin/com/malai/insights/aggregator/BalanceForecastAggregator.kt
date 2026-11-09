package com.malai.insights.aggregator

import com.malai.commons.model.InstrumentStatus.ACTIVE
import com.malai.commons.schedule.OccurrenceSchedule
import com.malai.insights.model.BalanceForecastResponse
import com.malai.insights.model.ForecastInstrument
import com.malai.insights.model.ForecastOccurrence
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Component
class BalanceForecastAggregator {

    fun aggregate(
        asOfDate: LocalDate,
        availableBalanceMinorUnits: Long,
        currency: String,
        instruments: List<ForecastInstrument>,
    ): BalanceForecastResponse {
        val month = YearMonth.from(asOfDate)
        val windowStart = month.atDay(1)
        val windowEnd = month.atEndOfMonth()

        val occurrences = instruments
            .filter { it.status == ACTIVE }
            .flatMap { projectOccurrences(it, windowStart, windowEnd) }
            .sortedWith(compareBy({ it.date }, { it.instrumentId }))

        val completed = occurrences.filter { !it.date.isAfter(asOfDate) }
        val expected = occurrences.filter { it.date.isAfter(asOfDate) }

        val completedTotal = completed.sumOf { it.amountMinorUnits }
        val expectedTotal = expected.sumOf { it.amountMinorUnits }

        return BalanceForecastResponse(
            month = month.toString(),
            windowStart = windowStart.toString(),
            windowEnd = windowEnd.toString(),
            asOfDate = asOfDate.toString(),
            currency = currency,
            availableBalanceMinorUnits = availableBalanceMinorUnits,
            completedTotalMinorUnits = completedTotal,
            expectedTotalMinorUnits = expectedTotal,
            forecastBalanceMinorUnits = availableBalanceMinorUnits + expectedTotal,
            completed = completed.map { it.toView() },
            expected = expected.map { it.toView() },
        )
    }

    private fun projectOccurrences(
        instrument: ForecastInstrument,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<ProjectedOccurrence> {
        val occurrences = mutableListOf<ProjectedOccurrence>()
        var date = OccurrenceSchedule.nextStrictlyAfter(
            instrument.startDate, instrument.frequency, windowStart.minusDays(1),
        )
        while (!date.isAfter(windowEnd)) {
            occurrences += ProjectedOccurrence(
                date = date,
                counterpartyName = instrument.counterpartyName,
                amountMinorUnits = -instrument.amountMinorUnits,
                type = instrument.type,
                instrumentId = instrument.instrumentId,
            )
            date = OccurrenceSchedule.nextStrictlyAfter(instrument.startDate, instrument.frequency, date)
        }
        return occurrences
    }

    private data class ProjectedOccurrence(
        val date: LocalDate,
        val counterpartyName: String,
        val amountMinorUnits: Long,
        val type: String,
        val instrumentId: UUID,
    ) {
        fun toView() = ForecastOccurrence(
            date = date.toString(),
            counterpartyName = counterpartyName,
            amountMinorUnits = amountMinorUnits,
            type = type,
            instrumentId = instrumentId,
        )
    }
}
