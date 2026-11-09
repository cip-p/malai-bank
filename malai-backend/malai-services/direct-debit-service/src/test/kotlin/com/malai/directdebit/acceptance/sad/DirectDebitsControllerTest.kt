package com.malai.directdebit.acceptance.sad

import com.malai.commons.model.InstrumentStatus
import com.malai.directdebit.acceptance.DirectDebitsAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID.randomUUID

/**
 * Cross-tenant DELETE returns 404 (not 403) — same pattern as StandingOrdersService:
 * `findByDirectDebitIdAndAccountHolderId(...) ?: throw NotFoundException(MANDATE_NOT_FOUND)`,
 * so unknown and not-owned ids are indistinguishable and no existence leaks.
 */
class DirectDebitsControllerTest : DirectDebitsAcceptanceTest() {

    @Test
    fun getReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(directDebitServiceReceives(
            get().url("/api/v1/direct-debits")
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
    fun deleteReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(directDebitServiceReceives(
            delete().url("/api/v1/direct-debits/${randomUUID()}")
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
    fun deleteUnknownDirectDebitReturns404() = malaiAcceptanceTest {
        whenever(directDebitServiceReceives(
            delete()
                .url("/api/v1/direct-debits/${randomUUID()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8994"
                noExtraProps()
            }
        }
    }

    @Test
    fun deleteAnotherHoldersDirectDebitReturns404AndLeavesItActive() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = anotherAccountHolderUid(),
            directDebitId = someMandateUid(),
        ))

        whenever(directDebitServiceReceives(
            delete()
                .url("/api/v1/direct-debits/${someMandateUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8994"
                noExtraProps()
            }
        }

        and(theDirectDebitInTheDatabase(someMandateUid())) {
            this!!.status shouldBe InstrumentStatus.ACTIVE
            this.cancelledAt shouldBe null
        }
    }
}
