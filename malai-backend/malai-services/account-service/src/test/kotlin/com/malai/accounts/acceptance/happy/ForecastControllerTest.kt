package com.malai.accounts.acceptance.happy

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.client.malai.directdebit.builders.response.ActiveDirectDebitsResponseBuilder.ActiveDirectDebitBuilder.Companion.anActiveDirectDebit
import com.malai.client.malai.directdebit.builders.response.ActiveDirectDebitsResponseBuilder.Companion.anActiveDirectDebitsResponse
import com.malai.client.malai.insights.builders.response.BalanceForecastResponseBuilder.Companion.aBalanceForecastResponse
import com.malai.client.malai.insights.builders.response.BalanceForecastResponseBuilder.ForecastOccurrenceBuilder.Companion.aForecastOccurrence
import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder.Companion.aBalancesResponse
import com.malai.client.malai.standingorder.builders.response.ActiveStandingOrdersResponseBuilder.ActiveStandingOrderBuilder.Companion.anActiveStandingOrder
import com.malai.client.malai.standingorder.builders.response.ActiveStandingOrdersResponseBuilder.Companion.anActiveStandingOrdersResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class ForecastControllerTest : AccountsAcceptanceTest() {

    private val rentOrderId = UUID.fromString("00000000-0000-4000-9000-0000000000a1")
    private val energyOrderId = UUID.fromString("00000000-0000-4000-9000-0000000000a2")
    private val broadbandMandateId = UUID.fromString("00000000-0000-4000-9000-0000000000b1")

    @Test
    fun composesTheForecastFromLedgerBalanceStandingOrdersDirectDebitsAndInsights() = malaiAcceptanceTest {
        given(nowIs("2026-07-09T10:00:00Z"))
        and(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withLedgerBalanceMinorUnits(100_000L)
            .withHeldAmountMinorUnits(0L)
            .withAvailableBalanceMinorUnits(100_000L)))
        and(getActiveStandingOrdersReturns(
            someAccountUid(),
            anActiveStandingOrdersResponse().withStandingOrders(
                anActiveStandingOrder()
                    .withStandingOrderId(rentOrderId)
                    .withAccountId(someAccountUid())
                    .withPayeeName("Landlord")
                    .withAmountMinorUnits(10_000L)
                    .withCurrency("GBP")
                    .withFrequency("MONTHLY")
                    .withStatus("ACTIVE")
                    .withStartDate("2026-01-05"),
                anActiveStandingOrder()
                    .withStandingOrderId(energyOrderId)
                    .withAccountId(someAccountUid())
                    .withPayeeName("Octopus Energy")
                    .withAmountMinorUnits(5_000L)
                    .withCurrency("GBP")
                    .withFrequency("MONTHLY")
                    .withStatus("ACTIVE")
                    .withStartDate("2026-03-20"),
            )))
        and(getActiveDirectDebitsReturns(
            someAccountUid(),
            anActiveDirectDebitsResponse().withDirectDebits(
                anActiveDirectDebit()
                    .withDirectDebitId(broadbandMandateId)
                    .withAccountId(someAccountUid())
                    .withCreditorName("Thames Water")
                    .withAmountMinorUnits(3_000L)
                    .withCurrency("GBP")
                    .withFrequency("MONTHLY")
                    .withStatus("ACTIVE")
                    .withStartDate("2026-02-12"),
            )))
        and(balanceForecastReturns(aBalanceForecastResponse()
            .withMonth("2026-07")
            .withWindowStart("2026-07-01")
            .withWindowEnd("2026-07-31")
            .withAsOfDate("2026-07-09")
            .withCurrency("GBP")
            .withAvailableBalanceMinorUnits(100_000L)
            .withCompletedTotalMinorUnits(-10_000L)
            .withExpectedTotalMinorUnits(-8_000L)
            .withForecastBalanceMinorUnits(92_000L)
            .withCompleted(
                aForecastOccurrence()
                    .withDate("2026-07-05")
                    .withCounterpartyName("Landlord")
                    .withAmountMinorUnits(-10_000L)
                    .withType("STANDING_ORDER")
                    .withInstrumentId(rentOrderId),
            )
            .withExpected(
                aForecastOccurrence()
                    .withDate("2026-07-12")
                    .withCounterpartyName("Thames Water")
                    .withAmountMinorUnits(-3_000L)
                    .withType("DIRECT_DEBIT")
                    .withInstrumentId(broadbandMandateId),
                aForecastOccurrence()
                    .withDate("2026-07-20")
                    .withCounterpartyName("Octopus Energy")
                    .withAmountMinorUnits(-5_000L)
                    .withType("STANDING_ORDER")
                    .withInstrumentId(energyOrderId),
            )))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/forecast")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "month" To "2026-07"
                "windowStart" To "2026-07-01"
                "windowEnd" To "2026-07-31"
                "asOfDate" To "2026-07-09"
                "currency" To "GBP"
                "availableBalanceMinorUnits" To 100_000
                "completedTotalMinorUnits" To -10_000
                "expectedTotalMinorUnits" To -8_000
                "forecastBalanceMinorUnits" To 92_000
                "completed" To listOf(
                    json {
                        "date" To "2026-07-05"
                        "counterpartyName" To "Landlord"
                        "amountMinorUnits" To -10_000
                        "type" To "STANDING_ORDER"
                        "instrumentId" To rentOrderId.toString()
                        noExtraProps()
                    },
                )
                "expected" To listOf(
                    json {
                        "date" To "2026-07-12"
                        "counterpartyName" To "Thames Water"
                        "amountMinorUnits" To -3_000
                        "type" To "DIRECT_DEBIT"
                        "instrumentId" To broadbandMandateId.toString()
                        noExtraProps()
                    },
                    json {
                        "date" To "2026-07-20"
                        "counterpartyName" To "Octopus Energy"
                        "amountMinorUnits" To -5_000
                        "type" To "STANDING_ORDER"
                        "instrumentId" To energyOrderId.toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url.startsWith("/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/balances")
        }) {
            method shouldBe GET
            headers["Authorization"]!! shouldStartWith "Bearer "
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url.startsWith("/standing-order-service/api/v1/internal/standing-orders") &&
                url.contains("accountId=${someAccountUid()}")
        }) {
            method shouldBe GET
            headers["Authorization"]!! shouldStartWith "Bearer "
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url.startsWith("/direct-debit-service/api/v1/internal/direct-debits") &&
                url.contains("accountId=${someAccountUid()}")
        }) {
            method shouldBe GET
            headers["Authorization"]!! shouldStartWith "Bearer "
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/balance-forecast"
        }) {
            method shouldBe POST
            headers["Authorization"]!! shouldStartWith "Bearer "
            headers["Content-Type"] shouldBe "application/json"
            body containsProp json {
                "asOfDate" To "2026-07-09"
                "availableBalanceMinorUnits" To 100_000
                "currency" To "GBP"
                "instruments" To listOf(
                    json {
                        "instrumentId" To rentOrderId.toString()
                        "type" To "STANDING_ORDER"
                        "counterpartyName" To "Landlord"
                        "amountMinorUnits" To 10_000
                        "frequency" To "MONTHLY"
                        "startDate" To "2026-01-05"
                        "status" To "ACTIVE"
                        noExtraProps()
                    },
                    json {
                        "instrumentId" To energyOrderId.toString()
                        "type" To "STANDING_ORDER"
                        "counterpartyName" To "Octopus Energy"
                        "amountMinorUnits" To 5_000
                        "frequency" To "MONTHLY"
                        "startDate" To "2026-03-20"
                        "status" To "ACTIVE"
                        noExtraProps()
                    },
                    json {
                        "instrumentId" To broadbandMandateId.toString()
                        "type" To "DIRECT_DEBIT"
                        "counterpartyName" To "Thames Water"
                        "amountMinorUnits" To 3_000
                        "frequency" To "MONTHLY"
                        "startDate" To "2026-02-12"
                        "status" To "ACTIVE"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }
}
