package com.malai.exec.acceptance.happy

import com.malai.client.malai.accountholder.builders.response.AccountHoldersCreatedResponseBuilder.Companion.anAccountHoldersCreatedResponse
import com.malai.client.malai.card.builders.response.CardsIssuedResponseBuilder.Companion.aCardsIssuedResponse
import com.malai.client.malai.payments.builders.response.DepositsTotalResponseBuilder.Companion.aDepositsTotalResponse
import com.malai.client.malai.payments.builders.response.PaymentsStoppedTotalResponseBuilder.Companion.aPaymentsStoppedTotalResponse
import com.malai.exec.acceptance.ExecAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class ExecMetricsControllerTest : ExecAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("00000000-0000-4000-9000-0000000000e1")
    private val someManagerUserId = UUID.fromString("00000000-0000-4000-9000-0000000000d1")

    @Test
    fun aManagerSeesTheSameBankingDayNumbersAsAnAdmin() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(12)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        whenever(execServiceReceives(theDashboardIsOpenedByAManager()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 12
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 1_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 5
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 250_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    @Test
    fun allFourHeadlineMetricsAreRealForTheBankingDay() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(12)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 12
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 1_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 5
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 250_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    @Test
    fun theDepositsWindowAskedOfPaymentsIsTheLondonDayNotTheUtcOne() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(12)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(execServiceMadeTheFollowingHttpRequest {
            url.startsWith("/payments-service/api/v1/internal/payments/deposits-total")
        }) {
            url shouldBe "/payments-service/api/v1/internal/payments/deposits-total" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun thePaymentsStoppedWindowAskedOfPaymentsIsTheLondonDayNotTheUtcOne() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(12)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(execServiceMadeTheFollowingHttpRequest {
            url.startsWith("/payments-service/api/v1/internal/payments/stopped-total")
        }) {
            url shouldBe "/payments-service/api/v1/internal/payments/stopped-total" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun theCardsIssuedWindowAskedOfCardServiceIsTheLondonDayNotTheUtcOne() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(12)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(execServiceMadeTheFollowingHttpRequest {
            url.startsWith("/card-service/api/v1/internal/cards/issued-count")
        }) {
            url shouldBe "/card-service/api/v1/internal/cards/issued-count" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun inWinterTheLondonDayAndTheUtcDayCoincide() = malaiAcceptanceTest {
        given(nowIs("2026-01-15T09:30:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(3)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(2)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(100_000)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-01-15"
                "bankingDay" To "2026-01-15"
                "asOf" To "2026-01-15T09:30:00Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 3
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 250_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 2
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 100_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
        and(execServiceMadeTheFollowingHttpRequest {
            url.startsWith("/card-service/api/v1/internal/cards/issued-count")
        }) {
            url shouldBe "/card-service/api/v1/internal/cards/issued-count" +
                "?from=2026-01-15T00%3A00%3A00Z&until=2026-01-16T00%3A00%3A00Z"
        }
    }

    @Test
    fun justAfterLondonMidnightTheDashboardShowsTheNewDayNotYesterdays() = malaiAcceptanceTest {
        given(nowIs("2026-07-19T23:00:01Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(0)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(0)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(0)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(0)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-19T23:00:01Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 0
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 0
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 0
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 0
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    @Test
    fun aQuietDayReportsZerosRatherThanUnavailable() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T08:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(0)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(0)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(0)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(0)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T08:00:00Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 0
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 0
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 0
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 0
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    @Test
    fun theLastSevenDaysWindowStartsSixDaysBeforeTheBankingDayAtLondonMidnight() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(84)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(7_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(35)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_750_000)))

        whenever(execServiceReceives(theDashboardIsOpenedForWindow("LAST_7_DAYS")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "LAST_7_DAYS"
                "windowStart" To "2026-07-14"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 84
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 7_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 35
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 1_750_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
        and(execServiceMadeTheFollowingHttpRequest {
            url.startsWith("/payments-service/api/v1/internal/payments/deposits-total")
        }) {
            url shouldBe "/payments-service/api/v1/internal/payments/deposits-total" +
                "?from=2026-07-13T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun theLastThirtyDaysWindowSpansTheClockChangeAnchoredToMidnightAtBothEnds() = malaiAcceptanceTest {
        given(nowIs("2026-04-10T10:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(12)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        whenever(execServiceReceives(theDashboardIsOpenedForWindow("LAST_30_DAYS")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "LAST_30_DAYS"
                "windowStart" To "2026-03-12"
                "bankingDay" To "2026-04-10"
                "asOf" To "2026-04-10T10:00:00Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 12
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 1_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 5
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 250_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
        and(execServiceMadeTheFollowingHttpRequest {
            url.startsWith("/card-service/api/v1/internal/cards/issued-count")
        }) {
            url shouldBe "/card-service/api/v1/internal/cards/issued-count" +
                "?from=2026-03-12T00%3A00%3A00Z&until=2026-04-10T23%3A00%3A00Z"
        }
    }

    @Test
    fun theLastTwelveMonthsWindowStartsTwelveCalendarMonthsBeforeTheBankingDay() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(4_120)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(365_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(1_900)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(91_000_000)))

        whenever(execServiceReceives(theDashboardIsOpenedForWindow("LAST_12_MONTHS")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "LAST_12_MONTHS"
                "windowStart" To "2025-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 4_120
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 365_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "AVAILABLE"
                    "count" To 1_900
                    noExtraProps()
                }
                "paymentsStopped" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 91_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
        and(execServiceMadeTheFollowingHttpRequest {
            url.startsWith("/payments-service/api/v1/internal/payments/deposits-total")
        }) {
            url shouldBe "/payments-service/api/v1/internal/payments/deposits-total" +
                "?from=2025-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    private fun theDashboardIsOpenedForWindow(window: String) =
        get()
            .url("/api/v1/exec/metrics")
            .queryParam("window", window)
            .withJwtTokenForUserId(someAdminUserId, listOf("admin"))

    private fun theDashboardIsOpenedByAnAdmin() =
        get()
            .url("/api/v1/exec/metrics")
            .withJwtTokenForUserId(someAdminUserId, listOf("admin"))

    private fun theDashboardIsOpenedByAManager() =
        get()
            .url("/api/v1/exec/metrics")
            .withJwtTokenForUserId(someManagerUserId, listOf("manager"))
}
