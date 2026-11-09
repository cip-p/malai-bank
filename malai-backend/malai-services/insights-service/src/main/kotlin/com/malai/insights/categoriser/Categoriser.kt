package com.malai.insights.categoriser

import com.malai.insights.model.Category
import com.malai.insights.model.Category.BILLS
import com.malai.insights.model.Category.EATING_OUT
import com.malai.insights.model.Category.GENERAL
import com.malai.insights.model.Category.GROCERIES
import com.malai.insights.model.Category.HOUSING
import com.malai.insights.model.Category.INCOME
import com.malai.insights.model.Category.PAYMENTS
import com.malai.insights.model.Category.SUBSCRIPTIONS
import com.malai.insights.model.Category.TRANSPORT
import com.malai.insights.model.Category.TRANSFERS
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class Categoriser {

    fun categorise(
        entryId: UUID,
        merchantName: String?,
        description: String?,
        amountMinorUnits: Long,
        transactionType: String? = null,
        override: Category? = null,
    ): Category {
        if (override != null) return override
        merchantName?.let { keywordMatch(it)?.let { matched -> return matched } }
        description?.let { keywordMatch(it)?.let { matched -> return matched } }
        return defaultTier(transactionType, amountMinorUnits)
    }

    private fun keywordMatch(text: String): Category? {
        val haystack = text.lowercase()
        return RULES.firstOrNull { haystack.contains(it.first) }?.second
    }

    private fun defaultTier(transactionType: String?, amountMinorUnits: Long): Category = when {
        amountMinorUnits > 0 -> INCOME
        transactionType in RAIL_PAYMENT_TYPES -> PAYMENTS
        transactionType in BOOK_TRANSFER_TYPES -> TRANSFERS
        else -> GENERAL
    }

    companion object {
        private val RULES: List<Pair<String, Category>> = listOf(
            "tesco" to GROCERIES,
            "tfl" to TRANSPORT,
            "pret" to EATING_OUT,
            "octopus energy" to BILLS,
            "council tax" to BILLS,
            "rent" to HOUSING,
            "spotify" to SUBSCRIPTIONS,
            "gym" to SUBSCRIPTIONS,
            "savings sweep" to TRANSFERS,
            "savings opening" to TRANSFERS,
            "credit card payment" to PAYMENTS,
            "acme salary" to INCOME,
        )

        private val RAIL_PAYMENT_TYPES: Set<String> = setOf(
            "PAYMENT",
            "PAYMENT_SETTLEMENT",
            "PAYMENT_CREDIT",
            "DIRECT_DEBIT_COLLECTION",
        )

        private val BOOK_TRANSFER_TYPES: Set<String> = setOf(
            "TRANSFER",
            "INCOMING_TRANSFER",
            "OPENING_BALANCE",
        )
    }
}
