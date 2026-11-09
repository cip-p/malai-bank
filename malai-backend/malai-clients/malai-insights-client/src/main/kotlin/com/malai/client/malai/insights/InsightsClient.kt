package com.malai.client.malai.insights

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.insights.model.BalanceForecastRequest
import com.malai.client.malai.insights.model.BalanceForecastResponse
import com.malai.client.malai.insights.model.CategorisationsResponse
import com.malai.client.malai.insights.model.CategoriseEntriesRequest
import com.malai.client.malai.insights.model.CategoryOverrideRequest
import com.malai.client.malai.insights.model.SpendingBreakdownRequest
import com.malai.client.malai.insights.model.SpendingBreakdownResponse
import org.apache.hc.client5.http.classic.methods.HttpDelete
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpPut
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID
import tools.jackson.module.kotlin.jacksonMapperBuilder

@Component
class InsightsClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.insights.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    private val requestMapper = jacksonMapperBuilder().changeDefaultPropertyInclusion { it.withValueInclusion(NON_NULL) }.build()

    fun categorise(
        serviceToken: String,
        request: CategoriseEntriesRequest,
    ): CategorisationsResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/insights/categorisations")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request), StandardCharsets.UTF_8)

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/insights/categorisations")
        return fromJson(responseBody, CategorisationsResponse::class.java)
    }

    fun spendingBreakdown(
        serviceToken: String,
        request: SpendingBreakdownRequest,
    ): SpendingBreakdownResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/insights/spending-breakdown")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request), StandardCharsets.UTF_8)

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/insights/spending-breakdown")
        return fromJson(responseBody, SpendingBreakdownResponse::class.java)
    }

    fun balanceForecast(
        serviceToken: String,
        request: BalanceForecastRequest,
    ): BalanceForecastResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/insights/balance-forecast")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request), StandardCharsets.UTF_8)

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/insights/balance-forecast")
        return fromJson(responseBody, BalanceForecastResponse::class.java)
    }

    fun putOverride(
        serviceToken: String,
        entryId: UUID,
        request: CategoryOverrideRequest,
    ) {
        val httpRequest = HttpPut("$malaiBaseUrl/api/v1/internal/insights/overrides/$entryId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request), StandardCharsets.UTF_8)

        makeRequest(httpRequest, 200)
    }

    fun deleteOverride(
        serviceToken: String,
        entryId: UUID,
    ) {
        val httpRequest = HttpDelete("$malaiBaseUrl/api/v1/internal/insights/overrides/$entryId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        makeRequest(httpRequest, 200)
    }
}
