package com.malai.insights.model

data class SpendingBreakdownRequest(
    val month: String,
    val entries: List<EntryFact>,
)

data class CategorisedAmount(
    val category: Category,
    val amountMinorUnits: Long,
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
