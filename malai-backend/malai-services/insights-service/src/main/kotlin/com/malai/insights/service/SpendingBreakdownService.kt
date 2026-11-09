package com.malai.insights.service

import com.malai.insights.aggregator.SpendingBreakdownAggregator
import com.malai.insights.categoriser.Categoriser
import com.malai.insights.model.CategorisedAmount
import com.malai.insights.model.SpendingBreakdownRequest
import com.malai.insights.model.SpendingBreakdownResponse
import org.springframework.stereotype.Service

@Service
class SpendingBreakdownService(
    private val categoriser: Categoriser,
    private val aggregator: SpendingBreakdownAggregator,
    private val overrideService: CategoryOverrideService,
) {

    fun breakdown(request: SpendingBreakdownRequest): SpendingBreakdownResponse {
        val overrides = overrideService.overridesFor(request.entries.map { it.entryId })
        return aggregator.aggregate(
            request.entries.map { fact ->
                CategorisedAmount(
                    category = categoriser.categorise(
                        entryId = fact.entryId,
                        merchantName = fact.merchantName,
                        description = fact.description,
                        amountMinorUnits = fact.amountMinorUnits,
                        transactionType = fact.transactionType,
                        override = overrides[fact.entryId],
                    ),
                    amountMinorUnits = fact.amountMinorUnits,
                )
            },
        )
    }
}
