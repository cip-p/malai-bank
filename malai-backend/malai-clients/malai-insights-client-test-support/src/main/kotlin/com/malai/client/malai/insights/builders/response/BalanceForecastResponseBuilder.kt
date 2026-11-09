package com.malai.client.malai.insights.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class BalanceForecastResponseBuilder private constructor() : TestBuilder {

    private var month: String = "2026-07"
    private var windowStart: String = "2026-07-01"
    private var windowEnd: String = "2026-07-31"
    private var asOfDate: String = "2026-07-09"
    private var currency: String = "GBP"
    private var availableBalanceMinorUnits: Long = 0
    private var completedTotalMinorUnits: Long = 0
    private var expectedTotalMinorUnits: Long = 0
    private var forecastBalanceMinorUnits: Long = 0
    private var completed: List<ForecastOccurrenceBuilder> = emptyList()
    private var expected: List<ForecastOccurrenceBuilder> = emptyList()

    fun withMonth(value: String) = apply { this.month = value }
    fun withWindowStart(value: String) = apply { this.windowStart = value }
    fun withWindowEnd(value: String) = apply { this.windowEnd = value }
    fun withAsOfDate(value: String) = apply { this.asOfDate = value }
    fun withCurrency(value: String) = apply { this.currency = value }
    fun withAvailableBalanceMinorUnits(value: Long) = apply { this.availableBalanceMinorUnits = value }
    fun withCompletedTotalMinorUnits(value: Long) = apply { this.completedTotalMinorUnits = value }
    fun withExpectedTotalMinorUnits(value: Long) = apply { this.expectedTotalMinorUnits = value }
    fun withForecastBalanceMinorUnits(value: Long) = apply { this.forecastBalanceMinorUnits = value }
    fun withCompleted(vararg occurrences: ForecastOccurrenceBuilder) = apply { this.completed = occurrences.toList() }
    fun withExpected(vararg occurrences: ForecastOccurrenceBuilder) = apply { this.expected = occurrences.toList() }

    override fun build(): String = JsonSerialiser.toJson(
        BalanceForecast(
            month = month,
            windowStart = windowStart,
            windowEnd = windowEnd,
            asOfDate = asOfDate,
            currency = currency,
            availableBalanceMinorUnits = availableBalanceMinorUnits,
            completedTotalMinorUnits = completedTotalMinorUnits,
            expectedTotalMinorUnits = expectedTotalMinorUnits,
            forecastBalanceMinorUnits = forecastBalanceMinorUnits,
            completed = completed.map { it.build() },
            expected = expected.map { it.build() },
        ),
    )

    data class BalanceForecast(
        val month: String,
        val windowStart: String,
        val windowEnd: String,
        val asOfDate: String,
        val currency: String,
        val availableBalanceMinorUnits: Long,
        val completedTotalMinorUnits: Long,
        val expectedTotalMinorUnits: Long,
        val forecastBalanceMinorUnits: Long,
        val completed: List<ForecastOccurrenceBuilder.ForecastOccurrence>,
        val expected: List<ForecastOccurrenceBuilder.ForecastOccurrence>,
    )

    class ForecastOccurrenceBuilder private constructor() {
        private lateinit var date: String
        private lateinit var counterpartyName: String
        private var amountMinorUnits: Long = 0
        private var type: String = "STANDING_ORDER"
        private var instrumentId: UUID = UUID.randomUUID()

        fun withDate(value: String) = apply { this.date = value }
        fun withCounterpartyName(value: String) = apply { this.counterpartyName = value }
        fun withAmountMinorUnits(value: Long) = apply { this.amountMinorUnits = value }
        fun withType(value: String) = apply { this.type = value }
        fun withInstrumentId(value: UUID) = apply { this.instrumentId = value }

        fun build() = ForecastOccurrence(date, counterpartyName, amountMinorUnits, type, instrumentId)

        data class ForecastOccurrence(
            val date: String,
            val counterpartyName: String,
            val amountMinorUnits: Long,
            val type: String,
            val instrumentId: UUID,
        )

        companion object {
            fun aForecastOccurrence() = ForecastOccurrenceBuilder()
        }
    }

    companion object {
        fun aBalanceForecastResponse() = BalanceForecastResponseBuilder()
    }
}
