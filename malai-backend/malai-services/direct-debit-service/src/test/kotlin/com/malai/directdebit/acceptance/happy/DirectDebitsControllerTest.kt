package com.malai.directdebit.acceptance.happy

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.directdebit.acceptance.DirectDebitsAcceptanceTest
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate
import java.util.UUID.randomUUID

/**
 * Plan 02-14 happy paths for `GET /api/v1/direct-debits` (BACK-12), extended by the
 * SO/DD lifecycle issue 02 with the mandate cancel transition.
 *
 * D-2.22 ownership filter: tests seed both an `accounts` row and a `direct_debits`
 * row sharing the same `account_holder_id` so the native SQL JOIN resolves.
 */
class DirectDebitsControllerTest : DirectDebitsAcceptanceTest() {

    @Test
    fun getReturnsHoldersDirectDebits() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            creditorName = "British Gas",
            creditorSortCode = "20-00-00",
            creditorAccountNumber = "12345678",
            amountMinorUnits = 4500,
            frequency = Frequency.MONTHLY,
            nextCollectionAt = Instant.parse("2026-06-01T08:00:00Z"),
        ))
        // Another holder's DD — must NOT appear in the response.
        and(anAccountAndDirectDebitExist(
            accountHolderId = anotherAccountHolderUid(),
            directDebitId = randomUUID(),
            creditorName = "Other holder's DD",
        ))

        whenever(directDebitServiceReceives(
            get()
                .url("/api/v1/direct-debits")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "directDebits" To listOf(
                    json {
                        "directDebitId" To someMandateUid().toString()
                        "accountId" To someAccountUid().toString()
                        "creditorName" To "British Gas"
                        "creditorSortCode" To "20-00-00"
                        "creditorAccountNumber" To "12345678"
                        "amountMinorUnits" To 4500
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "ACTIVE"
                        "startDate" To "2026-06-01"
                        "nextCollectionAt" To "2026-06-01T08:00:00Z"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getIncludesCancelledDirectDebitsWithCancellationTimestamp() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            creditorName = "Old gym",
            status = InstrumentStatus.CANCELLED,
            startDate = LocalDate.parse("2026-03-15"),
            cancelledAt = Instant.parse("2026-05-20T14:00:00Z"),
            nextCollectionAt = null,
        ))

        whenever(directDebitServiceReceives(
            get()
                .url("/api/v1/direct-debits")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "directDebits" To listOf(
                    json {
                        "directDebitId" To someMandateUid().toString()
                        "accountId" To someAccountUid().toString()
                        "creditorName" To "Old gym"
                        "creditorSortCode" To "20-00-00"
                        "creditorAccountNumber" To "12345678"
                        "amountMinorUnits" To 4500
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
    fun deleteOnAlreadyCancelledDirectDebitIsANoOp() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            status = InstrumentStatus.CANCELLED,
            startDate = LocalDate.parse("2026-06-01"),
            cancelledAt = Instant.parse("2026-05-01T09:00:00Z"),
            nextCollectionAt = Instant.parse("2026-06-01T08:00:00Z"),
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
        ))
        and(nowIs("2026-05-10T10:30:00Z"))

        whenever(directDebitServiceReceives(
            delete()
                .url("/api/v1/direct-debits/${someMandateUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theDirectDebitInTheDatabase(someMandateUid())) {
            this shouldBe DirectDebitEntity(
                directDebitId = someMandateUid(),
                accountId = someAccountUid(),
                creditorName = "British Gas",
                creditorSortCode = "20-00-00",
                creditorAccountNumber = "12345678",
                reference = null,
                amountMinorUnits = 4500,
                currency = Currency.GBP,
                frequency = Frequency.MONTHLY,
                status = InstrumentStatus.CANCELLED,
                startDate = LocalDate.parse("2026-06-01"),
                cancelledAt = Instant.parse("2026-05-01T09:00:00Z"),
                nextCollectionAt = Instant.parse("2026-06-01T08:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun deleteCancelsDirectDebitStampsTimestampAndWritesAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            creditorName = "British Gas",
            creditorSortCode = "20-00-00",
            creditorAccountNumber = "12345678",
            reference = "DD-BRITISH-",
            amountMinorUnits = 4500,
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.parse("2026-06-01"),
            nextCollectionAt = Instant.parse("2026-06-01T08:00:00Z"),
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
        ))
        and(nowIs("2026-05-10T10:30:00Z"))

        whenever(directDebitServiceReceives(
            delete()
                .url("/api/v1/direct-debits/${someMandateUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theDirectDebitInTheDatabase(someMandateUid())) {
            this shouldBe DirectDebitEntity(
                directDebitId = someMandateUid(),
                accountId = someAccountUid(),
                creditorName = "British Gas",
                creditorSortCode = "20-00-00",
                creditorAccountNumber = "12345678",
                reference = "DD-BRITISH-",
                amountMinorUnits = 4500,
                currency = Currency.GBP,
                frequency = Frequency.MONTHLY,
                status = InstrumentStatus.CANCELLED,
                startDate = LocalDate.parse("2026-06-01"),
                cancelledAt = Instant.parse("2026-05-10T10:30:00Z"),
                nextCollectionAt = Instant.parse("2026-06-01T08:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "DIRECT_DEBIT_CANCELLED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "DirectDebit"
            this[0]["resource_id"] shouldBe someMandateUid().toString()
        }
    }

    @Test
    fun getReturnsEmptyListWhenHolderHasNoDirectDebits() = malaiAcceptanceTest {
        whenever(directDebitServiceReceives(
            get()
                .url("/api/v1/direct-debits")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "directDebits" To emptyList<Any>()
                noExtraProps()
            }
        }
    }
}
