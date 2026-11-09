package com.malai.insights.acceptance.happy

import com.malai.insights.acceptance.InsightsAcceptanceTest
import com.malai.insights.model.BalanceForecastRequest
import com.malai.insights.model.ForecastInstrumentFact
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class InsightsBalanceForecastControllerTest : InsightsAcceptanceTest() {

    private val rentId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")
    private val energyId = UUID.fromString("00000000-0000-4000-7000-0000000000c2")

    @Test
    fun projectsActiveInstrumentsIntoCompletedAndExpectedOccurrencesForTheMonth() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/balance-forecast")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(BalanceForecastRequest(
                    asOfDate = "2026-07-09",
                    availableBalanceMinorUnits = 100_000L,
                    currency = "GBP",
                    instruments = listOf(
                        ForecastInstrumentFact(rentId, "STANDING_ORDER", "Landlord", 10_000L, "MONTHLY", "2026-01-05", "ACTIVE"),
                        ForecastInstrumentFact(energyId, "STANDING_ORDER", "Octopus Energy", 5_000L, "MONTHLY", "2026-03-20", "ACTIVE"),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "month" To "2026-07"
                "windowStart" To "2026-07-01"
                "windowEnd" To "2026-07-31"
                "asOfDate" To "2026-07-09"
                "currency" To "GBP"
                "availableBalanceMinorUnits" To 100_000
                "completedTotalMinorUnits" To -10_000
                "expectedTotalMinorUnits" To -5_000
                "forecastBalanceMinorUnits" To 95_000
                "completed" To listOf(
                    json {
                        "date" To "2026-07-05"
                        "counterpartyName" To "Landlord"
                        "amountMinorUnits" To -10_000
                        "type" To "STANDING_ORDER"
                        "instrumentId" To rentId.toString()
                        noExtraProps()
                    },
                )
                "expected" To listOf(
                    json {
                        "date" To "2026-07-20"
                        "counterpartyName" To "Octopus Energy"
                        "amountMinorUnits" To -5_000
                        "type" To "STANDING_ORDER"
                        "instrumentId" To energyId.toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun accountWithNoActiveInstrumentsForecastsToItsAvailableBalance() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/balance-forecast")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(BalanceForecastRequest(
                    asOfDate = "2026-07-09",
                    availableBalanceMinorUnits = 42_000L,
                    currency = "GBP",
                    instruments = emptyList(),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "month" To "2026-07"
                "windowStart" To "2026-07-01"
                "windowEnd" To "2026-07-31"
                "asOfDate" To "2026-07-09"
                "currency" To "GBP"
                "availableBalanceMinorUnits" To 42_000
                "completedTotalMinorUnits" To 0
                "expectedTotalMinorUnits" To 0
                "forecastBalanceMinorUnits" To 42_000
                "completed" To emptyList<Any>()
                "expected" To emptyList<Any>()
                noExtraProps()
            }
        }
    }
}
