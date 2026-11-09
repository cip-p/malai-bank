package com.malai.insights.aggregator

import com.malai.insights.model.CategorisedAmount
import com.malai.insights.model.Category
import com.malai.insights.model.Category.INCOME
import com.malai.insights.model.Category.TRANSFERS
import com.malai.insights.model.SpendingBreakdownResponse
import com.malai.insights.model.SpendingCategoryTotal
import org.springframework.stereotype.Component

@Component
class SpendingBreakdownAggregator {

    fun aggregate(entries: List<CategorisedAmount>): SpendingBreakdownResponse {
        val categories = entries
            .filter { it.amountMinorUnits < 0 && it.category !in NON_SPENDING }
            .groupBy { it.category }
            .map { (category, group) ->
                SpendingCategoryTotal(
                    category = category,
                    totalMinorUnits = group.sumOf { -it.amountMinorUnits },
                    transactionCount = group.size,
                )
            }
            .sortedWith(compareByDescending<SpendingCategoryTotal> { it.totalMinorUnits }.thenBy { it.category.ordinal })

        return SpendingBreakdownResponse(
            categories = categories,
            totalMinorUnits = categories.sumOf { it.totalMinorUnits },
        )
    }

    companion object {
        private val NON_SPENDING: Set<Category> = setOf(INCOME, TRANSFERS)
    }
}
