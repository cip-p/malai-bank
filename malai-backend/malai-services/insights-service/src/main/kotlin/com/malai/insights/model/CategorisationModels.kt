package com.malai.insights.model

import java.util.UUID

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
