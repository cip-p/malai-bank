package com.malai.insights.aggregator

import com.malai.insights.model.CategorisedAmount
import com.malai.insights.model.Category.BILLS
import com.malai.insights.model.Category.GENERAL
import com.malai.insights.model.Category.GROCERIES
import com.malai.insights.model.Category.INCOME
import com.malai.insights.model.Category.TRANSFERS
import com.malai.insights.model.SpendingCategoryTotal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpendingBreakdownAggregatorTest {

    private val aggregator = SpendingBreakdownAggregator()

    @Test
    fun sumsDebitsPerCategoryAsPositiveMagnitudesLargestFirst() {
        val breakdown = aggregator.aggregate(
            listOf(
                CategorisedAmount(GROCERIES, -3_000L),
                CategorisedAmount(GROCERIES, -1_200L),
                CategorisedAmount(BILLS, -7_800L),
            ),
        )

        assertThat(breakdown.categories).containsExactly(
            SpendingCategoryTotal(BILLS, 7_800L, 1),
            SpendingCategoryTotal(GROCERIES, 4_200L, 2),
        )
        assertThat(breakdown.totalMinorUnits).isEqualTo(12_000L)
    }

    @Test
    fun excludesCreditsEntirely() {
        val breakdown = aggregator.aggregate(
            listOf(
                CategorisedAmount(GROCERIES, -3_000L),
                CategorisedAmount(INCOME, 240_000L),
                CategorisedAmount(GENERAL, 500L),
            ),
        )

        assertThat(breakdown.categories).containsExactly(
            SpendingCategoryTotal(GROCERIES, 3_000L, 1),
        )
        assertThat(breakdown.totalMinorUnits).isEqualTo(3_000L)
    }

    @Test
    fun dropsIncomeAndTransfersFromTheSpendingFilter() {
        val breakdown = aggregator.aggregate(
            listOf(
                CategorisedAmount(GROCERIES, -3_000L),
                CategorisedAmount(TRANSFERS, -20_000L),
                CategorisedAmount(INCOME, -100L),
            ),
        )

        assertThat(breakdown.categories).containsExactly(
            SpendingCategoryTotal(GROCERIES, 3_000L, 1),
        )
        assertThat(breakdown.totalMinorUnits).isEqualTo(3_000L)
    }

    @Test
    fun emptyInputYieldsEmptyCategoriesAndZeroTotal() {
        val breakdown = aggregator.aggregate(emptyList())

        assertThat(breakdown.categories).isEmpty()
        assertThat(breakdown.totalMinorUnits).isEqualTo(0L)
    }

    @Test
    fun monthWithNoDebitsYieldsEmptyCategoriesAndZeroTotal() {
        val breakdown = aggregator.aggregate(
            listOf(
                CategorisedAmount(INCOME, 240_000L),
                CategorisedAmount(TRANSFERS, -20_000L),
            ),
        )

        assertThat(breakdown.categories).isEmpty()
        assertThat(breakdown.totalMinorUnits).isEqualTo(0L)
    }
}
