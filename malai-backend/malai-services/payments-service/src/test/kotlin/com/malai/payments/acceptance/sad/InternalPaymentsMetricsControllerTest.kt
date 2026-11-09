package com.malai.payments.acceptance.sad

import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED

class InternalPaymentsMetricsControllerTest : PaymentsAcceptanceTest() {

    private val windowStart = "2026-07-19T23:00:00Z"
    private val windowEnd = "2026-07-20T23:00:00Z"

    @Test
    fun withoutAServiceJwtTheDepositsTotalIsRefused() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/deposits-total")
                .queryParam("from", windowStart)
                .queryParam("until", windowEnd)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun aCustomerJwtCannotReachTheInternalDepositsTotal() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/deposits-total")
                .queryParam("from", windowStart)
                .queryParam("until", windowEnd)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5987"
                noExtraProps()
            }
        }
    }

    @Test
    fun withoutAServiceJwtThePaymentsStoppedTotalIsRefused() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/stopped-total")
                .queryParam("from", windowStart)
                .queryParam("until", windowEnd)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun aCustomerJwtCannotReachTheInternalPaymentsStoppedTotal() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/stopped-total")
                .queryParam("from", windowStart)
                .queryParam("until", windowEnd)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5987"
                noExtraProps()
            }
        }
    }

    @Test
    fun aWindowMissingItsUpperBoundIsRejected() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/deposits-total")
                .queryParam("from", windowStart)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun anUnparseableWindowBoundIsRejected() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/deposits-total")
                .queryParam("from", "the-start-of-the-day")
                .queryParam("until", windowEnd)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }
}
