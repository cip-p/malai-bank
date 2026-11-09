package com.malai.insights.controller

import com.malai.insights.model.SpendingBreakdownRequest
import com.malai.insights.model.SpendingBreakdownResponse
import com.malai.insights.service.SpendingBreakdownService
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/insights/spending-breakdown")
class InsightsSpendingBreakdownController(
    private val service: SpendingBreakdownService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun breakdown(@Valid @RequestBody request: SpendingBreakdownRequest): SpendingBreakdownResponse =
        service.breakdown(request)
}
