package com.malai.insights.controller

import com.malai.insights.model.BalanceForecastRequest
import com.malai.insights.model.BalanceForecastResponse
import com.malai.insights.service.BalanceForecastService
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/insights/balance-forecast")
class InsightsBalanceForecastController(
    private val service: BalanceForecastService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun forecast(@Valid @RequestBody request: BalanceForecastRequest): BalanceForecastResponse =
        service.forecast(request)
}
