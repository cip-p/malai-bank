package com.malai.client.malai.insights

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.insights.builders.response.BalanceForecastResponseBuilder.Companion.aBalanceForecastResponse
import com.malai.client.malai.insights.builders.response.BalanceForecastResponseBuilder.ForecastOccurrenceBuilder.Companion.aForecastOccurrence
import com.malai.client.malai.insights.builders.response.CategorisationsResponseBuilder.Companion.aCategorisationsResponse
import com.malai.client.malai.insights.builders.response.CategorisationsResponseBuilder.CategorisationBuilder.Companion.aCategorisation
import com.malai.client.malai.insights.builders.response.SpendingBreakdownResponseBuilder.Companion.aSpendingBreakdownResponse
import com.malai.client.malai.insights.builders.response.SpendingBreakdownResponseBuilder.CategoryTotalBuilder.Companion.aCategoryTotal
import com.malai.client.malai.insights.model.BalanceForecastRequest
import com.malai.client.malai.insights.model.BalanceForecastResponse
import com.malai.client.malai.insights.model.Categorisation
import com.malai.client.malai.insights.model.CategorisationsResponse
import com.malai.client.malai.insights.model.Category.BILLS
import com.malai.client.malai.insights.model.Category.GENERAL
import com.malai.client.malai.insights.model.Category.GROCERIES
import com.malai.client.malai.insights.model.CategoriseEntriesRequest
import com.malai.client.malai.insights.model.CategoryOverrideRequest
import com.malai.client.malai.insights.model.EntryFact
import com.malai.client.malai.insights.model.ForecastInstrumentFact
import com.malai.client.malai.insights.model.ForecastOccurrence
import com.malai.client.malai.insights.model.SpendingBreakdownRequest
import com.malai.client.malai.insights.model.SpendingBreakdownResponse
import com.malai.client.malai.insights.model.SpendingCategoryTotal
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpMethod.PUT
import java.util.UUID

@MalaiClientSpringTest
class InsightsClientTest : MalaiClientTest(), WithInsightsApi {

    @Autowired
    private lateinit var insightsClient: InsightsClient

    private var lastCategorisationsResponse: CategorisationsResponse? = null
    private var lastSpendingBreakdownResponse: SpendingBreakdownResponse? = null
    private var lastBalanceForecastResponse: BalanceForecastResponse? = null

    private val someEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000e1")
    private val anotherEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000e2")
    private val someInstrumentId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")

    @Test
    fun categorise() {
        given(categoriseReturns(aCategorisationsResponse().withCategorisations(
            aCategorisation().withEntryId(someEntryId).withCategory("GROCERIES"),
            aCategorisation().withEntryId(anotherEntryId).withCategory("GENERAL"),
        )))

        whenever(clientCallsCategorise(
            "some-service-token",
            CategoriseEntriesRequest(entries = listOf(
                EntryFact(entryId = someEntryId, amountMinorUnits = -3_000L, merchantName = "Tesco weekly shop"),
                EntryFact(entryId = anotherEntryId, amountMinorUnits = -1_200L, merchantName = "Corner Shop"),
            )),
        ))

        then(theLastCategorisationsResponse()) {
            this shouldBe CategorisationsResponse(listOf(
                Categorisation(someEntryId, GROCERIES),
                Categorisation(anotherEntryId, GENERAL),
            ))
        }
        and(madeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/categorisations"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "entries" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "amountMinorUnits" To -3_000
                        "merchantName" To "Tesco weekly shop"
                        noExtraProps()
                    },
                    json {
                        "entryId" To anotherEntryId.toString()
                        "amountMinorUnits" To -1_200
                        "merchantName" To "Corner Shop"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun spendingBreakdown() {
        given(spendingBreakdownReturns(aSpendingBreakdownResponse()
            .withCategories(
                aCategoryTotal().withCategory("BILLS").withTotalMinorUnits(7_800L).withTransactionCount(1),
                aCategoryTotal().withCategory("GROCERIES").withTotalMinorUnits(4_200L).withTransactionCount(2),
            )
            .withTotalMinorUnits(12_000L)))

        whenever(clientCallsSpendingBreakdown(
            "some-service-token",
            SpendingBreakdownRequest(month = "2026-05", entries = listOf(
                EntryFact(entryId = someEntryId, amountMinorUnits = -7_800L, merchantName = "Octopus Energy — DD"),
                EntryFact(entryId = anotherEntryId, amountMinorUnits = -3_000L, merchantName = "Tesco weekly shop"),
            )),
        ))

        then(theLastSpendingBreakdownResponse()) {
            this shouldBe SpendingBreakdownResponse(
                categories = listOf(
                    SpendingCategoryTotal(BILLS, 7_800L, 1),
                    SpendingCategoryTotal(GROCERIES, 4_200L, 2),
                ),
                totalMinorUnits = 12_000L,
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/spending-breakdown"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "month" To "2026-05"
                "entries" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "amountMinorUnits" To -7_800
                        "merchantName" To "Octopus Energy — DD"
                        noExtraProps()
                    },
                    json {
                        "entryId" To anotherEntryId.toString()
                        "amountMinorUnits" To -3_000
                        "merchantName" To "Tesco weekly shop"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun balanceForecast() {
        given(balanceForecastReturns(aBalanceForecastResponse()
            .withMonth("2026-07")
            .withWindowStart("2026-07-01")
            .withWindowEnd("2026-07-31")
            .withAsOfDate("2026-07-09")
            .withCurrency("GBP")
            .withAvailableBalanceMinorUnits(100_000L)
            .withCompletedTotalMinorUnits(-10_000L)
            .withExpectedTotalMinorUnits(-5_000L)
            .withForecastBalanceMinorUnits(95_000L)
            .withCompleted(
                aForecastOccurrence()
                    .withDate("2026-07-05")
                    .withCounterpartyName("Landlord")
                    .withAmountMinorUnits(-10_000L)
                    .withType("STANDING_ORDER")
                    .withInstrumentId(someInstrumentId),
            )
            .withExpected(
                aForecastOccurrence()
                    .withDate("2026-07-20")
                    .withCounterpartyName("Octopus Energy")
                    .withAmountMinorUnits(-5_000L)
                    .withType("STANDING_ORDER")
                    .withInstrumentId(someInstrumentId),
            )))

        whenever(clientCallsBalanceForecast(
            "some-service-token",
            BalanceForecastRequest(
                asOfDate = "2026-07-09",
                availableBalanceMinorUnits = 100_000L,
                currency = "GBP",
                instruments = listOf(
                    ForecastInstrumentFact(
                        instrumentId = someInstrumentId,
                        type = "STANDING_ORDER",
                        counterpartyName = "Landlord",
                        amountMinorUnits = 10_000L,
                        frequency = "MONTHLY",
                        startDate = "2026-01-05",
                        status = "ACTIVE",
                    ),
                ),
            ),
        ))

        then(theLastBalanceForecastResponse()) {
            this shouldBe BalanceForecastResponse(
                month = "2026-07",
                windowStart = "2026-07-01",
                windowEnd = "2026-07-31",
                asOfDate = "2026-07-09",
                currency = "GBP",
                availableBalanceMinorUnits = 100_000L,
                completedTotalMinorUnits = -10_000L,
                expectedTotalMinorUnits = -5_000L,
                forecastBalanceMinorUnits = 95_000L,
                completed = listOf(
                    ForecastOccurrence("2026-07-05", "Landlord", -10_000L, "STANDING_ORDER", someInstrumentId),
                ),
                expected = listOf(
                    ForecastOccurrence("2026-07-20", "Octopus Energy", -5_000L, "STANDING_ORDER", someInstrumentId),
                ),
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/balance-forecast"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "asOfDate" To "2026-07-09"
                "availableBalanceMinorUnits" To 100_000
                "currency" To "GBP"
                "instruments" To listOf(
                    json {
                        "instrumentId" To someInstrumentId.toString()
                        "type" To "STANDING_ORDER"
                        "counterpartyName" To "Landlord"
                        "amountMinorUnits" To 10_000
                        "frequency" To "MONTHLY"
                        "startDate" To "2026-01-05"
                        "status" To "ACTIVE"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun putOverride() {
        given(putOverrideReturns(someEntryId))

        whenever(clientCallsPutOverride("some-service-token", someEntryId, CategoryOverrideRequest(GROCERIES)))

        then(madeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/overrides/$someEntryId"
        }) {
            method shouldBe PUT
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "category" To "GROCERIES"
                noExtraProps()
            }
        }
    }

    @Test
    fun deleteOverride() {
        given(deleteOverrideReturns(someEntryId))

        whenever(clientCallsDeleteOverride("some-service-token", someEntryId))

        then(madeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/overrides/$someEntryId"
        }) {
            method shouldBe DELETE
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    private fun clientCallsCategorise(serviceToken: String, request: CategoriseEntriesRequest) =
        Action<ActionContext> {
            lastCategorisationsResponse = insightsClient.categorise(serviceToken, request)
        }

    private fun clientCallsPutOverride(serviceToken: String, entryId: UUID, request: CategoryOverrideRequest) =
        Action<ActionContext> {
            insightsClient.putOverride(serviceToken, entryId, request)
        }

    private fun clientCallsDeleteOverride(serviceToken: String, entryId: UUID) =
        Action<ActionContext> {
            insightsClient.deleteOverride(serviceToken, entryId)
        }

    private fun clientCallsSpendingBreakdown(serviceToken: String, request: SpendingBreakdownRequest) =
        Action<ActionContext> {
            lastSpendingBreakdownResponse = insightsClient.spendingBreakdown(serviceToken, request)
        }

    private fun clientCallsBalanceForecast(serviceToken: String, request: BalanceForecastRequest) =
        Action<ActionContext> {
            lastBalanceForecastResponse = insightsClient.balanceForecast(serviceToken, request)
        }

    private fun theLastCategorisationsResponse() = StateCollector { _ ->
        lastCategorisationsResponse ?: error("No categorisations response captured")
    }

    private fun theLastSpendingBreakdownResponse() = StateCollector { _ ->
        lastSpendingBreakdownResponse ?: error("No spending breakdown response captured")
    }

    private fun theLastBalanceForecastResponse() = StateCollector { _ ->
        lastBalanceForecastResponse ?: error("No balance forecast response captured")
    }
}
