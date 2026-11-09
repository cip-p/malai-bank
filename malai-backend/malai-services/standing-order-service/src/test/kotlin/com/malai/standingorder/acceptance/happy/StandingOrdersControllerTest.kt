package com.malai.standingorder.acceptance.happy

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.standingorder.acceptance.StandingOrdersAcceptanceTest
import com.malai.standingorder.builders.request.StandingOrderRequestBuilder.Companion.aStandingOrderRequest
import com.malai.standingorder.entity.StandingOrderEntity
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate
import java.util.UUID.randomUUID

/**
 * Plan 02-14 happy paths for `GET /api/v1/standing-orders` (BACK-12), extended by the
 * SO/DD lifecycle issue 01 with the cancel transition.
 *
 * D-2.22 ownership filter pattern: tests seed both an `accounts` row and a
 * `standing_orders` row sharing the same `account_holder_id`.
 */
class StandingOrdersControllerTest : StandingOrdersAcceptanceTest() {

    @Test
    fun getReturnsHoldersStandingOrders() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = someAccountUid(),
            payeeName = "Landlord",
            payeeSortCode = "20-00-00",
            payeeAccountNumber = "12345678",
            amountMinorUnits = 95000,
            frequency = Frequency.MONTHLY,
            nextPaymentAt = Instant.parse("2026-06-01T08:00:00Z"),
        ))
        // Another holder's SO — must NOT appear in the response.
        and(anAccountAndStandingOrderExist(
            accountHolderId = anotherAccountHolderUid(),
            standingOrderId = randomUUID(),
            payeeName = "Other holder's SO",
        ))

        whenever(standingOrderServiceReceives(
            get()
                .url("/api/v1/standing-orders")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "standingOrders" To listOf(
                    json {
                        "standingOrderId" To someMandateUid().toString()
                        "accountId" To someAccountUid().toString()
                        "payeeName" To "Landlord"
                        "payeeSortCode" To "20-00-00"
                        "payeeAccountNumber" To "12345678"
                        "amountMinorUnits" To 95000
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "ACTIVE"
                        "startDate" To "2026-06-01"
                        "nextPaymentAt" To "2026-06-01T08:00:00Z"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getIncludesCancelledStandingOrdersWithCancellationTimestamp() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = someAccountUid(),
            payeeName = "Old gym",
            status = InstrumentStatus.CANCELLED,
            startDate = LocalDate.parse("2026-03-15"),
            cancelledAt = Instant.parse("2026-05-20T14:00:00Z"),
            nextPaymentAt = null,
        ))

        whenever(standingOrderServiceReceives(
            get()
                .url("/api/v1/standing-orders")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "standingOrders" To listOf(
                    json {
                        "standingOrderId" To someMandateUid().toString()
                        "accountId" To someAccountUid().toString()
                        "payeeName" To "Old gym"
                        "payeeSortCode" To "20-00-00"
                        "payeeAccountNumber" To "12345678"
                        "amountMinorUnits" To 95000
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "CANCELLED"
                        "startDate" To "2026-03-15"
                        "cancelledAt" To "2026-05-20T14:00:00Z"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun deleteCancelsStandingOrderStampsTimestampAndWritesAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = someAccountUid(),
            payeeName = "Landlord",
            payeeSortCode = "20-00-00",
            payeeAccountNumber = "12345678",
            reference = "SO-RENT",
            amountMinorUnits = 95000,
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.parse("2026-06-01"),
            nextPaymentAt = Instant.parse("2026-06-01T08:00:00Z"),
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
        ))
        and(nowIs("2026-05-10T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            delete()
                .url("/api/v1/standing-orders/${someMandateUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theStandingOrderInTheDatabase(someMandateUid())) {
            this shouldBe StandingOrderEntity(
                standingOrderId = someMandateUid(),
                accountId = someAccountUid(),
                payeeId = null,
                payeeName = "Landlord",
                payeeSortCode = "20-00-00",
                payeeAccountNumber = "12345678",
                reference = "SO-RENT",
                amountMinorUnits = 95000,
                currency = Currency.GBP,
                frequency = Frequency.MONTHLY,
                status = InstrumentStatus.CANCELLED,
                startDate = LocalDate.parse("2026-06-01"),
                cancelledAt = Instant.parse("2026-05-10T10:30:00Z"),
                nextPaymentAt = Instant.parse("2026-06-01T08:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "STANDING_ORDER_CANCELLED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "StandingOrder"
            this[0]["resource_id"] shouldBe someMandateUid().toString()
        }
    }

    @Test
    fun deleteOnAlreadyCancelledStandingOrderIsANoOp() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = someAccountUid(),
            status = InstrumentStatus.CANCELLED,
            startDate = LocalDate.parse("2026-06-01"),
            cancelledAt = Instant.parse("2026-05-01T09:00:00Z"),
            nextPaymentAt = Instant.parse("2026-06-01T08:00:00Z"),
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
        ))
        and(nowIs("2026-05-10T10:30:00Z"))

        whenever(standingOrderServiceReceives(
            delete()
                .url("/api/v1/standing-orders/${someMandateUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theStandingOrderInTheDatabase(someMandateUid())) {
            this shouldBe StandingOrderEntity(
                standingOrderId = someMandateUid(),
                accountId = someAccountUid(),
                payeeId = null,
                payeeName = "Landlord",
                payeeSortCode = "20-00-00",
                payeeAccountNumber = "12345678",
                reference = null,
                amountMinorUnits = 95000,
                currency = Currency.GBP,
                frequency = Frequency.MONTHLY,
                status = InstrumentStatus.CANCELLED,
                startDate = LocalDate.parse("2026-06-01"),
                cancelledAt = Instant.parse("2026-05-01T09:00:00Z"),
                nextPaymentAt = Instant.parse("2026-06-01T08:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postCreatesActiveStandingOrderWithPayeeSnapshotAndWritesAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(
            payeeId = somePayeeUid(),
            accountHolderId = someAccountHolderUid(),
            name = "Landlord",
            sortCode = "20-00-00",
            accountNumber = "12345678",
        ))
        and(nowIs("2026-07-01T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(someMandateUid()))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "create-standing-order-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid())
                    .withAmountMinorUnits(95000)
                    .withCurrency("GBP")
                    .withReference("SO-RENT")
                    .withFrequency("MONTHLY")
                    .withStartDate("2026-07-15"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "standingOrderId" To someMandateUid().toString()
                "accountId" To someAccountUid().toString()
                "payeeId" To somePayeeUid().toString()
                "payeeName" To "Landlord"
                "payeeSortCode" To "20-00-00"
                "payeeAccountNumber" To "12345678"
                "reference" To "SO-RENT"
                "amountMinorUnits" To 95000
                "currency" To "GBP"
                "frequency" To "MONTHLY"
                "status" To "ACTIVE"
                "startDate" To "2026-07-15"
                "nextPaymentAt" To "2026-07-15T00:00:00Z"
                noExtraProps()
            }
        }

        and(theStandingOrderInTheDatabase(someMandateUid())) {
            this shouldBe StandingOrderEntity(
                standingOrderId = someMandateUid(),
                accountId = someAccountUid(),
                payeeId = somePayeeUid(),
                payeeName = "Landlord",
                payeeSortCode = "20-00-00",
                payeeAccountNumber = "12345678",
                reference = "SO-RENT",
                amountMinorUnits = 95000,
                currency = Currency.GBP,
                frequency = Frequency.MONTHLY,
                status = InstrumentStatus.ACTIVE,
                startDate = LocalDate.parse("2026-07-15"),
                cancelledAt = null,
                nextPaymentAt = Instant.parse("2026-07-15T00:00:00Z"),
                createdAt = Instant.parse("2026-07-01T10:30:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "STANDING_ORDER_CREATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "StandingOrder"
            this[0]["resource_id"] shouldBe someMandateUid().toString()
        }
    }

    @Test
    fun postAcceptsStartDateOfTodayForTheImmediateExecutionDemoBeat() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))
        and(nowIs("2026-07-01T23:30:00Z"))
        and(theNextGeneratedUuidWillBe(someMandateUid()))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-today-key")
                .header("Content-Type", "application/json")
                .body(aStandingOrderRequest()
                    .withAccountId(someAccountUid())
                    .withPayeeId(somePayeeUid())
                    .withStartDate("2026-07-01"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "standingOrderId" To someMandateUid().toString()
                "accountId" To someAccountUid().toString()
                "payeeId" To somePayeeUid().toString()
                "payeeName" To "Landlord"
                "payeeSortCode" To "20-00-00"
                "payeeAccountNumber" To "12345678"
                "reference" To "SO-RENT"
                "amountMinorUnits" To 95000
                "currency" To "GBP"
                "frequency" To "MONTHLY"
                "status" To "ACTIVE"
                "startDate" To "2026-07-01"
                "nextPaymentAt" To "2026-07-01T00:00:00Z"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReplayWithSameKeyReturnsCachedResponseAndNoSecondOrderOrAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(aPayeeExists(payeeId = somePayeeUid(), accountHolderId = someAccountHolderUid()))
        and(nowIs("2026-07-01T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(someMandateUid()))

        val request = aStandingOrderRequest()
            .withAccountId(someAccountUid())
            .withPayeeId(somePayeeUid())

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-replay-key")
                .header("Content-Type", "application/json")
                .body(request)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(standingOrderServiceReceives(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", "standing-order-replay-key")
                .header("Content-Type", "application/json")
                .body(request)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(theStandingOrdersInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 1
            this[0].standingOrderId shouldBe someMandateUid()
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "STANDING_ORDER_CREATED"
        }
    }

    @Test
    fun getReturnsEmptyListWhenHolderHasNoStandingOrders() = malaiAcceptanceTest {
        whenever(standingOrderServiceReceives(
            get()
                .url("/api/v1/standing-orders")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "standingOrders" To emptyList<Any>()
                noExtraProps()
            }
        }
    }
}
