package com.malai.accountholder.acceptance.sad

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED

class InternalAccountHolderMetricsControllerTest : AccountHolderAcceptanceTest() {

    private val windowStart = "2026-07-19T23:00:00Z"
    private val windowEnd = "2026-07-20T23:00:00Z"

    @Test
    fun withoutAServiceJwtTheCountIsRefused() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/internal/account-holders/created-count")
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
    fun aCustomerJwtCannotReachTheInternalCount() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/internal/account-holders/created-count")
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
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/internal/account-holders/created-count")
                .queryParam("from", windowStart)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun anUnparseableWindowBoundIsRejected() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/internal/account-holders/created-count")
                .queryParam("from", "the-start-of-the-day")
                .queryParam("until", windowEnd)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }
}
