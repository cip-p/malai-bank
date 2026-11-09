package com.malai.insights.controller

import com.malai.insights.model.CategoryOverrideRequest
import com.malai.insights.service.CategoryOverrideService
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/insights/overrides")
class InsightsOverridesController(
    private val service: CategoryOverrideService,
) {

    @PutMapping("/{entryId}", consumes = [APPLICATION_JSON_VALUE])
    fun put(@PathVariable entryId: UUID, @Valid @RequestBody request: CategoryOverrideRequest) {
        service.put(entryId, request.category)
    }

    @DeleteMapping("/{entryId}")
    fun delete(@PathVariable entryId: UUID) {
        service.delete(entryId)
    }
}
