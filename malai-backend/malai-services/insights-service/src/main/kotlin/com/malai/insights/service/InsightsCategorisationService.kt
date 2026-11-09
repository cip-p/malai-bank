package com.malai.insights.service

import com.malai.insights.categoriser.Categoriser
import com.malai.insights.model.Categorisation
import com.malai.insights.model.CategorisationsResponse
import com.malai.insights.model.CategoriseEntriesRequest
import org.springframework.stereotype.Service

@Service
class InsightsCategorisationService(
    private val categoriser: Categoriser,
    private val overrideService: CategoryOverrideService,
) {

    fun categorise(request: CategoriseEntriesRequest): CategorisationsResponse {
        val overrides = overrideService.overridesFor(request.entries.map { it.entryId })
        return CategorisationsResponse(
            categorisations = request.entries.map { fact ->
                Categorisation(
                    entryId = fact.entryId,
                    category = categoriser.categorise(
                        entryId = fact.entryId,
                        merchantName = fact.merchantName,
                        description = fact.description,
                        amountMinorUnits = fact.amountMinorUnits,
                        transactionType = fact.transactionType,
                        override = overrides[fact.entryId],
                    ),
                )
            },
        )
    }
}
