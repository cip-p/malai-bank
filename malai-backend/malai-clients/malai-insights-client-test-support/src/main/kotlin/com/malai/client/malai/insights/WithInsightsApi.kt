package com.malai.client.malai.insights

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.insights.builders.response.BalanceForecastResponseBuilder
import com.malai.client.malai.insights.builders.response.CategorisationsResponseBuilder
import com.malai.client.malai.insights.builders.response.SpendingBreakdownResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

interface WithInsightsApi : WithWiremock {

    fun categoriseReturns(response: CategorisationsResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/categorisations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build()),
                ),
        )
    }

    fun categoriseReturnsServerError() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/categorisations"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "insights unavailable"}"""),
                ),
        )
    }

    fun spendingBreakdownReturns(response: SpendingBreakdownResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/spending-breakdown"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build()),
                ),
        )
    }

    fun spendingBreakdownReturnsServerError() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/spending-breakdown"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "insights unavailable"}"""),
                ),
        )
    }

    fun balanceForecastReturns(response: BalanceForecastResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/balance-forecast"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build()),
                ),
        )
    }

    fun balanceForecastReturnsServerError() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/balance-forecast"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "insights unavailable"}"""),
                ),
        )
    }

    fun putOverrideReturns(entryId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            put(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/overrides/$entryId"))
                .willReturn(aResponse().withStatus(200)),
        )
    }

    fun deleteOverrideReturns(entryId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            delete(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/insights/overrides/$entryId"))
                .willReturn(aResponse().withStatus(200)),
        )
    }

    companion object {
        const val CONTEXT_PATH = "/insights-service"
    }
}
