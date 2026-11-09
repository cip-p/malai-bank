package com.malai.insights.controller

import com.malai.insights.model.CategorisationsResponse
import com.malai.insights.model.CategoriseEntriesRequest
import com.malai.insights.service.InsightsCategorisationService
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/insights/categorisations")
class InsightsCategorisationsController(
    private val service: InsightsCategorisationService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun categorise(@Valid @RequestBody request: CategoriseEntriesRequest): CategorisationsResponse =
        service.categorise(request)
}
