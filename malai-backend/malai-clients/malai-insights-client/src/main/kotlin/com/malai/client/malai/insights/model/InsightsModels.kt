package com.malai.client.malai.insights.model

import java.util.UUID

enum class Category {
    INCOME,
    GROCERIES,
    EATING_OUT,
    TRANSPORT,
    BILLS,
    HOUSING,
    SUBSCRIPTIONS,
    TRANSFERS,
    PAYMENTS,
    GENERAL,
}

data class CategoriseEntriesRequest(
    val entries: List<EntryFact>,
)

data class EntryFact(
    val entryId: UUID,
    val amountMinorUnits: Long,
    val merchantName: String? = null,
    val description: String? = null,
    val transactionType: String? = null,
)

data class CategorisationsResponse(
    val categorisations: List<Categorisation>,
)

data class Categorisation(
    val entryId: UUID,
    val category: Category,
)

data class SpendingBreakdownRequest(
    val month: String,
    val entries: List<EntryFact>,
)

data class SpendingBreakdownResponse(
    val categories: List<SpendingCategoryTotal>,
    val totalMinorUnits: Long,
)

data class SpendingCategoryTotal(
    val category: Category,
    val totalMinorUnits: Long,
    val transactionCount: Int,
)

data class CategoryOverrideRequest(
    val category: Category,
)

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
