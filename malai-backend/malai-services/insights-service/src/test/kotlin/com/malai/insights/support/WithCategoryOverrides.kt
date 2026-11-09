package com.malai.insights.support

import com.malai.insights.entity.CategoryOverrideEntity
import com.malai.insights.model.Category
import com.malai.insights.repository.CategoryOverrideRepository
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import java.time.Instant
import java.util.UUID

interface WithCategoryOverrides : WithApplicationContext {

    fun categoryOverrideRepository(): CategoryOverrideRepository =
        getBean(CategoryOverrideRepository::class.java)

    fun aCategoryOverrideExists(
        entryId: UUID,
        category: Category,
        createdAt: Instant = Instant.parse("2026-05-01T09:00:00Z"),
        updatedAt: Instant = Instant.parse("2026-05-01T09:00:00Z"),
        createdBy: UUID? = null,
    ) = CallableAction {
        categoryOverrideRepository().save(
            CategoryOverrideEntity(
                entryId = entryId,
                category = category,
                createdAt = createdAt,
                updatedAt = updatedAt,
                createdBy = createdBy,
            )
        )
    }

    fun theCategoryOverrideInTheDatabase(entryId: UUID) = StateCollector<CategoryOverrideEntity?> { _ ->
        categoryOverrideRepository().findById(entryId).orElse(null)
    }
}
