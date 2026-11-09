package com.malai.directdebit.acceptance.sad

import com.malai.directdebit.acceptance.DirectDebitsAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID

/**
 * SO/DD lifecycle issue 09 sad paths for the internal mandate-lodgement endpoint: the authoritative
 * start-date-in-the-past guard and the service-token requirement.
 */
class InternalDirectDebitsControllerTest : DirectDebitsAcceptanceTest() {

    @Test
    fun lodgeWithStartDateInThePastReturns400AndLodgesNothing() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(nowIs("2026-06-02T10:00:00Z"))

        whenever(directDebitServiceReceives(
            post()
                .url("/api/v1/internal/direct-debits")
                .header("Content-Type", "application/json")
                .body(aLodgeRequestBody(startDate = "2026-06-01"))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7970"
                noExtraProps()
            }
        }

        and(theDirectDebitsInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun getActiveDirectDebitsWithoutServiceTokenReturns401() = malaiAcceptanceTest {
        whenever(directDebitServiceReceives(
            get()
                .url("/api/v1/internal/direct-debits")
                .queryParam("accountId", UUID.fromString("00000000-0000-4000-8000-0000000000d4"))
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
    fun lodgeWithoutServiceTokenReturns401() = malaiAcceptanceTest {
        whenever(directDebitServiceReceives(
            post()
                .url("/api/v1/internal/direct-debits")
                .header("Content-Type", "application/json")
                .body(aLodgeRequestBody(startDate = "2026-06-01"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    private fun aLodgeRequestBody(startDate: String): String =
        """{"accountId":"${someAccountUid()}","creditorName":"British Gas","creditorSortCode":"20-00-00","creditorAccountNumber":"12345678","reference":"DD-GAS","amountMinorUnits":4500,"currency":"GBP","frequency":"MONTHLY","startDate":"$startDate"}"""
}
