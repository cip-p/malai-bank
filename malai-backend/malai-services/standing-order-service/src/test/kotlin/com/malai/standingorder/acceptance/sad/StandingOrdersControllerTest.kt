package com.malai.standingorder.acceptance.sad

import com.malai.commons.model.InstrumentStatus
import com.malai.standingorder.acceptance.StandingOrdersAcceptanceTest
import com.malai.standingorder.builders.request.StandingOrderRequestBuilder.Companion.aStandingOrderRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import java.util.UUID.randomUUID

/**
 * Cross-tenant DELETE returns 404 (not 403) — same pattern as PayeesService:
 * `findByStandingOrderIdAndAccountHolderId(...) ?: throw NotFoundException(STANDING_ORDER_NOT_FOUND)`,
 * so unknown and not-owned ids are indistinguishable and no existence leaks.
 */
class StandingOrdersControllerTest : StandingOrdersAcceptanceTest() {

    @Test
    fun getReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(standingOrderServiceReceives(
            get().url("/api/v1/standing-orders")
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
        whenever(standingOrderServiceReceives(
            delete().url("/api/v1/standing-orders/${randomUUID()}")
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
    fun postReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(randomUUID())
                    .withPayeeId(randomUUID()))
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
    fun postWithNonPositiveAmountReturns400() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-zero-amount-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid())
                    .withAmountMinorUnits(0))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postWithoutIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid()))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7991"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postReplayWithDifferentBodyReturns422() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(theAuditChainIsAtGenesis())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))
        and(nowIs("2026-07-01T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(someMandateUid()))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-mismatch-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid())
                    .withAmountMinorUnits(95000))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-mismatch-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid())
                    .withAmountMinorUnits(100000))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7990"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun postWithKeyStillInFlightReturns409AndCreatesNothing() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anInFlightIdempotencyKeyExists("standing-order-in-flight-key"))
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-in-flight-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid()))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6996"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postWithStartDateInThePastReturns400AndCreatesNothing() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))
        and(nowIs("2026-07-01T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-past-date-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid())
                    .withStartDate("2026-06-30"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7972"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postWithNonGbpCurrencyReturns400AndCreatesNothing() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))
        and(nowIs("2026-07-01T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-usd-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid())
                    .withCurrency("USD"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7971"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postWithUnknownPayeeReturns404PayeeNotFound() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(nowIs("2026-07-01T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-unknown-payee-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid()))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8995"
                noExtraProps()
            }
        }
    }

    @Test
    fun postWithAnotherHoldersPayeeReturns404NotForbidden() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = anotherAccountHolderUid()))
        and(nowIs("2026-07-01T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-foreign-payee-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid()))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8995"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(anotherAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postWithUnknownAccountReturns404AccountNotFound() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))
        and(nowIs("2026-07-01T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-unknown-account-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid()))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postWithAnotherHoldersAccountReturns404NotForbidden() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = anotherAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))
        and(nowIs("2026-07-01T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-foreign-account-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid()))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }

        and(theStandingOrdersInTheDatabaseFor(anotherAccountHolderUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun deleteUnknownStandingOrderReturns404() = malaiAcceptanceTest {
        whenever(standingOrderServiceReceives(
            delete()
                .url("/api/v1/standing-orders/${randomUUID()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8991"
                noExtraProps()
            }
        }
    }

    @Test
    fun deleteAnotherHoldersStandingOrderReturns404AndLeavesItActive() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = anotherAccountHolderUid(),
            standingOrderId = someMandateUid(),
        ))

        whenever(standingOrderServiceReceives(
            delete()
                .url("/api/v1/standing-orders/${someMandateUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8991"
                noExtraProps()
            }
        }

        and(theStandingOrderInTheDatabase(someMandateUid())) {
            this!!.status shouldBe InstrumentStatus.ACTIVE
            this.cancelledAt shouldBe null
        }
    }
}
