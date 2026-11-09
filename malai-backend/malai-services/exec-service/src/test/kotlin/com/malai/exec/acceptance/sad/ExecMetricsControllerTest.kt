package com.malai.exec.acceptance.sad

import com.malai.client.malai.accountholder.builders.response.AccountHoldersCreatedResponseBuilder.Companion.anAccountHoldersCreatedResponse
import com.malai.client.malai.card.builders.response.CardsIssuedResponseBuilder.Companion.aCardsIssuedResponse
import com.malai.client.malai.payments.builders.response.DepositsTotalResponseBuilder.Companion.aDepositsTotalResponse
import com.malai.client.malai.payments.builders.response.PaymentsStoppedTotalResponseBuilder.Companion.aPaymentsStoppedTotalResponse
import com.malai.exec.acceptance.ExecAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID

class ExecMetricsControllerTest : ExecAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("00000000-0000-4000-9000-0000000000e1")
    private val someCustomerUserId = UUID.fromString("00000000-0000-4000-9000-0000000000c1")

    @Test
    fun aBrokenAccountHolderSourceCostsItsOwnTileWhileTheOtherThreeStayValued() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedFailsWithServerError())
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
                    "status" To "UNAVAILABLE"
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
    fun anUnreachableDepositsReadDegradesDepositsAloneEvenThoughPaymentsStoppedSharesItsService() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(7)))
        and(depositsTotalFailsWithServerError())
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
                    "count" To 7
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "UNAVAILABLE"
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
    fun aBrokenPaymentsStoppedReadDegradesPaymentsStoppedAloneEvenThoughDepositsSharesItsService() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(7)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalFailsWithServerError())

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
                    "count" To 7
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
                    "status" To "UNAVAILABLE"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    @Test
    fun aBrokenCardSourceDegradesCardsIssuedAloneWhileTheOthersStayValued() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(7)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedFailsWithServerError())
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
                    "count" To 7
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 1_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "UNAVAILABLE"
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
    fun anUnavailableMetricNamesNoReasonAndNoSickService() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(7)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedFailsWithServerError())
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))

        then(theLastResponseHas()) {
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 7
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 1_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "UNAVAILABLE"
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
    fun aSlowDepositsReadDegradesDepositsWithinItsTimeoutRatherThanHangingTheScreen() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(7)))
        and(depositsTotalTakesLongerThan(5_000, aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        val startedAt = System.currentTimeMillis()
        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))
        val elapsedMillis = System.currentTimeMillis() - startedAt

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 7
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "UNAVAILABLE"
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
        and(theElapsedMillis(elapsedMillis)) {
            this shouldBeLessThan 5_000L
        }
    }

    @Test
    fun aSlowPaymentsStoppedReadDegradesPaymentsStoppedWithinItsTimeoutRatherThanHangingTheScreen() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(7)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedReturns(aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalTakesLongerThan(5_000, aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        val startedAt = System.currentTimeMillis()
        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))
        val elapsedMillis = System.currentTimeMillis() - startedAt

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 7
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
                    "status" To "UNAVAILABLE"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
        and(theElapsedMillis(elapsedMillis)) {
            this shouldBeLessThan 5_000L
        }
    }

    @Test
    fun aSlowCardSourceDegradesCardsIssuedWithinItsTimeoutRatherThanHangingTheScreen() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(7)))
        and(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))
        and(cardsIssuedTakesLongerThan(5_000, aCardsIssuedResponse().withCount(5)))
        and(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(250_000)))

        val startedAt = System.currentTimeMillis()
        whenever(execServiceReceives(theDashboardIsOpenedByAnAdmin()))
        val elapsedMillis = System.currentTimeMillis() - startedAt

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "window" To "TODAY"
                "windowStart" To "2026-07-20"
                "bankingDay" To "2026-07-20"
                "asOf" To "2026-07-20T14:03:11Z"
                "newCustomers" To json {
                    "status" To "AVAILABLE"
                    "count" To 7
                    noExtraProps()
                }
                "deposits" To json {
                    "status" To "AVAILABLE"
                    "amountMinorUnits" To 1_000_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "cardsIssued" To json {
                    "status" To "UNAVAILABLE"
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
        and(theElapsedMillis(elapsedMillis)) {
            this shouldBeLessThan 5_000L
        }
    }

    @Test
    fun aCustomerCannotSeeTheBanksNumbers() = malaiAcceptanceTest {
        whenever(execServiceReceives(
            get()
                .url("/api/v1/exec/metrics")
                .withCustomerJwtTokenForUserId(someCustomerUserId)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun anUnauthenticatedCallerCannotSeeTheBanksNumbers() = malaiAcceptanceTest {
        whenever(execServiceReceives(get().url("/api/v1/exec/metrics")))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    private fun theElapsedMillis(elapsedMillis: Long) = dev.kensa.StateCollector { _ -> elapsedMillis }

    @Test
    fun anUnknownWindowIsRefusedAsABadRequestNeverSilentlyServedAsToday() = malaiAcceptanceTest {
        given(nowIs("2026-07-20T14:03:11Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())

        whenever(
            execServiceReceives(
                get()
                    .url("/api/v1/exec/metrics")
                    .queryParam("window", "LAST_FORTNIGHT")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    private fun theDashboardIsOpenedByAnAdmin() =
        get()
            .url("/api/v1/exec/metrics")
            .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
}
