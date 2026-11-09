package com.malai.client.malai.insights.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class CategorisationsResponseBuilder private constructor() : TestBuilder {

    private var categorisations: List<CategorisationBuilder> = emptyList()

    fun withCategorisations(vararg categorisations: CategorisationBuilder) = apply {
        this.categorisations = categorisations.toList()
    }

    override fun build(): String = JsonSerialiser.toJson(
        Categorisations(categorisations.map { it.build() }),
    )

    data class Categorisations(
        val categorisations: List<CategorisationBuilder.Categorisation>,
    )

    class CategorisationBuilder private constructor() {

        private lateinit var entryId: UUID
        private lateinit var category: String

        fun withEntryId(entryId: UUID) = apply { this.entryId = entryId }
        fun withCategory(category: String) = apply { this.category = category }

        fun build() = Categorisation(entryId, category)

        data class Categorisation(
            val entryId: UUID,
            val category: String,
        )

        companion object {
            fun aCategorisation() = CategorisationBuilder()
                .withEntryId(UUID.randomUUID())
                .withCategory("GENERAL")
        }
    }

    companion object {
        fun aCategorisationsResponse() = CategorisationsResponseBuilder()
    }
}
