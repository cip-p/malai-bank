package com.malai.insights.service

import com.malai.commons.TimeService
import com.malai.insights.entity.CategoryOverrideEntity
import com.malai.insights.model.Category
import com.malai.insights.repository.CategoryOverrideRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CategoryOverrideService(
    private val repository: CategoryOverrideRepository,
    private val timeService: TimeService,
) {

    fun overridesFor(entryIds: Collection<UUID>): Map<UUID, Category> {
        if (entryIds.isEmpty()) return emptyMap()
        return repository.findAllById(entryIds).associate { it.entryId to it.category }
    }

    @Transactional
    fun put(entryId: UUID, category: Category) {
        val now = timeService.now()
        val existing = repository.findById(entryId).orElse(null)
        val entity = existing?.copy(category = category, updatedAt = now)
            ?: CategoryOverrideEntity(entryId = entryId, category = category, createdAt = now, updatedAt = now)
        repository.save(entity)
    }

    @Transactional
    fun delete(entryId: UUID) {
        if (repository.existsById(entryId)) repository.deleteById(entryId)
    }
}
