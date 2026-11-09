package com.malai.client.malai.insights.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class SpendingBreakdownResponseBuilder private constructor() : TestBuilder {

    private var categories: List<CategoryTotalBuilder> = emptyList()
    private var totalMinorUnits: Long = 0

    fun withCategories(vararg categories: CategoryTotalBuilder) = apply {
        this.categories = categories.toList()
    }

    fun withTotalMinorUnits(totalMinorUnits: Long) = apply { this.totalMinorUnits = totalMinorUnits }

    override fun build(): String = JsonSerialiser.toJson(
        SpendingBreakdown(categories.map { it.build() }, totalMinorUnits),
    )

    data class SpendingBreakdown(
        val categories: List<CategoryTotalBuilder.CategoryTotal>,
        val totalMinorUnits: Long,
    )

    class CategoryTotalBuilder private constructor() {

        private lateinit var category: String
        private var totalMinorUnits: Long = 0
        private var transactionCount: Int = 0

        fun withCategory(category: String) = apply { this.category = category }
        fun withTotalMinorUnits(totalMinorUnits: Long) = apply { this.totalMinorUnits = totalMinorUnits }
        fun withTransactionCount(transactionCount: Int) = apply { this.transactionCount = transactionCount }

        fun build() = CategoryTotal(category, totalMinorUnits, transactionCount)

        data class CategoryTotal(
            val category: String,
            val totalMinorUnits: Long,
            val transactionCount: Int,
        )

        companion object {
            fun aCategoryTotal() = CategoryTotalBuilder()
                .withCategory("GENERAL")
        }
    }

    companion object {
        fun aSpendingBreakdownResponse() = SpendingBreakdownResponseBuilder()
    }
}
