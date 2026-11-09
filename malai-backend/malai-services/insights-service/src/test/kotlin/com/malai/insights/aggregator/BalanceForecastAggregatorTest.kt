package com.malai.insights.aggregator

import com.malai.commons.model.Frequency.MONTHLY
import com.malai.commons.model.Frequency.WEEKLY
import com.malai.commons.model.InstrumentStatus.ACTIVE
import com.malai.commons.model.InstrumentStatus.CANCELLED
import com.malai.insights.model.ForecastInstrument
import com.malai.insights.model.ForecastOccurrence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class BalanceForecastAggregatorTest {

    private val aggregator = BalanceForecastAggregator()

    private val rentId = UUID.fromString("00000000-0000-4000-a000-000000000001")
    private val gymId = UUID.fromString("00000000-0000-4000-a000-000000000002")
    private val energyId = UUID.fromString("00000000-0000-4000-a000-000000000003")

    @Test
    fun splitsOccurrencesIntoCompletedAndExpectedAroundAsOfDateWithSignedTotals() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-09"),
            availableBalanceMinorUnits = 100_000L,
            currency = "GBP",
            instruments = listOf(
                anInstrument(rentId, "Landlord", 10_000L, MONTHLY, "2026-01-05"),
                anInstrument(gymId, "Gym", 2_500L, MONTHLY, "2026-05-09"),
                anInstrument(energyId, "Octopus Energy", 5_000L, MONTHLY, "2026-03-20"),
            ),
        )

        assertThat(forecast.month).isEqualTo("2026-07")
        assertThat(forecast.windowStart).isEqualTo("2026-07-01")
        assertThat(forecast.windowEnd).isEqualTo("2026-07-31")
        assertThat(forecast.asOfDate).isEqualTo("2026-07-09")
        assertThat(forecast.currency).isEqualTo("GBP")
        assertThat(forecast.availableBalanceMinorUnits).isEqualTo(100_000L)

        assertThat(forecast.completed).containsExactly(
            ForecastOccurrence("2026-07-05", "Landlord", -10_000L, "STANDING_ORDER", rentId),
            ForecastOccurrence("2026-07-09", "Gym", -2_500L, "STANDING_ORDER", gymId),
        )
        assertThat(forecast.expected).containsExactly(
            ForecastOccurrence("2026-07-20", "Octopus Energy", -5_000L, "STANDING_ORDER", energyId),
        )

        assertThat(forecast.completedTotalMinorUnits).isEqualTo(-12_500L)
        assertThat(forecast.expectedTotalMinorUnits).isEqualTo(-5_000L)
        assertThat(forecast.forecastBalanceMinorUnits).isEqualTo(95_000L)
    }

    @Test
    fun occurrenceDatedExactlyTodayIsCompletedNotExpected() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-09"),
            availableBalanceMinorUnits = 50_000L,
            currency = "GBP",
            instruments = listOf(anInstrument(gymId, "Gym", 2_500L, MONTHLY, "2026-05-09")),
        )

        assertThat(forecast.completed).extracting<String> { it.date }.containsExactly("2026-07-09")
        assertThat(forecast.expected).isEmpty()
        assertThat(forecast.expectedTotalMinorUnits).isEqualTo(0L)
        assertThat(forecast.forecastBalanceMinorUnits).isEqualTo(50_000L)
    }

    @Test
    fun forecastGoesNegativeWhenExpectedOutgoingsExceedAvailable() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-09"),
            availableBalanceMinorUnits = 3_000L,
            currency = "GBP",
            instruments = listOf(anInstrument(energyId, "Octopus Energy", 5_000L, MONTHLY, "2026-03-20")),
        )

        assertThat(forecast.expectedTotalMinorUnits).isEqualTo(-5_000L)
        assertThat(forecast.forecastBalanceMinorUnits).isEqualTo(-2_000L)
    }

    @Test
    fun ordersOccurrencesByDateThenInstrumentIdTiebreak() {
        val earlierId = UUID.fromString("00000000-0000-4000-a000-0000000000a1")
        val laterId = UUID.fromString("00000000-0000-4000-a000-0000000000b2")

        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-01"),
            availableBalanceMinorUnits = 100_000L,
            currency = "GBP",
            instruments = listOf(
                anInstrument(laterId, "Second", 1_000L, MONTHLY, "2026-04-15"),
                anInstrument(earlierId, "First", 1_000L, MONTHLY, "2026-04-15"),
            ),
        )

        assertThat(forecast.expected).extracting<UUID> { it.instrumentId }
            .containsExactly(earlierId, laterId)
    }

    @Test
    fun clampsMonthlyOccurrenceToMonthEndInAShorterMonth() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-02-15"),
            availableBalanceMinorUnits = 100_000L,
            currency = "GBP",
            instruments = listOf(anInstrument(rentId, "Landlord", 10_000L, MONTHLY, "2026-01-31")),
        )

        assertThat(forecast.windowEnd).isEqualTo("2026-02-28")
        assertThat(forecast.expected).extracting<String> { it.date }.containsExactly("2026-02-28")
    }

    @Test
    fun includesEachWeeklyOccurrenceWithinTheMonth() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-16"),
            availableBalanceMinorUnits = 100_000L,
            currency = "GBP",
            instruments = listOf(anInstrument(gymId, "Gym", 1_000L, WEEKLY, "2026-07-01")),
        )

        assertThat(forecast.completed).extracting<String> { it.date }
            .containsExactly("2026-07-01", "2026-07-08", "2026-07-15")
        assertThat(forecast.expected).extracting<String> { it.date }
            .containsExactly("2026-07-22", "2026-07-29")
        assertThat(forecast.completedTotalMinorUnits).isEqualTo(-3_000L)
        assertThat(forecast.expectedTotalMinorUnits).isEqualTo(-2_000L)
    }

    @Test
    fun excludesNonActiveInstruments() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-09"),
            availableBalanceMinorUnits = 100_000L,
            currency = "GBP",
            instruments = listOf(
                anInstrument(rentId, "Landlord", 10_000L, MONTHLY, "2026-03-20"),
                anInstrument(gymId, "Cancelled Gym", 2_500L, MONTHLY, "2026-03-20", status = CANCELLED),
            ),
        )

        assertThat(forecast.expected).extracting<UUID> { it.instrumentId }.containsExactly(rentId)
        assertThat(forecast.expectedTotalMinorUnits).isEqualTo(-10_000L)
    }

    @Test
    fun includesAnInstrumentStartingMidMonthFromItsStartDateOnly() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-10"),
            availableBalanceMinorUnits = 100_000L,
            currency = "GBP",
            instruments = listOf(anInstrument(rentId, "Landlord", 10_000L, MONTHLY, "2026-07-20")),
        )

        assertThat(forecast.completed).isEmpty()
        assertThat(forecast.expected).extracting<String> { it.date }.containsExactly("2026-07-20")
    }

    @Test
    fun emptyInstrumentsYieldForecastEqualToAvailableWithEmptyLists() {
        val forecast = aggregator.aggregate(
            asOfDate = LocalDate.parse("2026-07-09"),
            availableBalanceMinorUnits = 42_000L,
            currency = "GBP",
            instruments = emptyList(),
        )

        assertThat(forecast.completed).isEmpty()
        assertThat(forecast.expected).isEmpty()
        assertThat(forecast.completedTotalMinorUnits).isEqualTo(0L)
        assertThat(forecast.expectedTotalMinorUnits).isEqualTo(0L)
        assertThat(forecast.forecastBalanceMinorUnits).isEqualTo(42_000L)
    }

    private fun anInstrument(
        instrumentId: UUID,
        counterpartyName: String,
        amountMinorUnits: Long,
        frequency: com.malai.commons.model.Frequency,
        startDate: String,
        status: com.malai.commons.model.InstrumentStatus = ACTIVE,
    ) = ForecastInstrument(
        instrumentId = instrumentId,
        type = "STANDING_ORDER",
        counterpartyName = counterpartyName,
        amountMinorUnits = amountMinorUnits,
        frequency = frequency,
        startDate = LocalDate.parse(startDate),
        status = status,
    )
}
