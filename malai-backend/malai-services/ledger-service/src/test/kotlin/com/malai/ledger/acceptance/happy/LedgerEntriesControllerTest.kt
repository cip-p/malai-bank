package com.malai.ledger.acceptance.happy

import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class LedgerEntriesControllerTest : LedgerAcceptanceTest() {

    private val someHoldId = UUID.fromString("00000000-0000-4000-7000-0000000000b1")

    @Test
    fun listEntriesReturnsAllEntriesNewestFirstWithRunningBalance() = malaiAcceptanceTest {
        val fundingAccountId = randomUUID()
        given(thereIsNoLedgerData())
        and(aBalancedEntryPair(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            amountMinorUnits = 1L,
            postedAt = Instant.parse("2026-04-30T09:00:00Z"),
        ))
        and(aBalancedEntryPair(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            amountMinorUnits = 10_000L,
            postedAt = Instant.parse("2026-05-01T10:00:00Z"),
        ))
        and(aBalancedEntryPair(
            fromAccountId = someAccountUid(),
            toAccountId = fundingAccountId,
            amountMinorUnits = 2_500L,
            postedAt = Instant.parse("2026-05-02T11:00:00Z"),
        ))
        and(aBalancedEntryPair(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            amountMinorUnits = 500L,
            postedAt = Instant.parse("2026-05-03T12:00:00Z"),
            transactionId = someTransactionId(),
            description = "some-description",
            counterpartyId = someCounterpartyId(),
            merchantName = "some-merchant-name",
            category = "some-category",
            type = "DIRECT_DEBIT_COLLECTION",
        ))

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "entries" To listOf(
                    json {
                        "entryId" To something
                        "transactionId" To someTransactionId().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "amountMinorUnits" To 500
                        "runningBalanceMinorUnits" To 8001
                        "description" To "some-description"
                        "counterpartyId" To someCounterpartyId().toString()
                        "merchantName" To "some-merchant-name"
                        "category" To "some-category"
                        "transactionType" To "DIRECT_DEBIT_COLLECTION"
                        noExtraProps()
                    },
                    json {
                        "entryId" To something
                        "transactionId" To something
                        "postedAt" To "2026-05-02T11:00:00Z"
                        "amountMinorUnits" To -2500
                        "runningBalanceMinorUnits" To 7501
                        "transactionType" To "FIXTURE"
                        noExtraProps()
                    },
                    json {
                        "entryId" To something
                        "transactionId" To something
                        "postedAt" To "2026-05-01T10:00:00Z"
                        "amountMinorUnits" To 10000
                        "runningBalanceMinorUnits" To 10001
                        "transactionType" To "FIXTURE"
                        noExtraProps()
                    },
                    json {
                        "entryId" To something
                        "transactionId" To something
                        "postedAt" To "2026-04-30T09:00:00Z"
                        "amountMinorUnits" To 1
                        "runningBalanceMinorUnits" To 1
                        "transactionType" To "FIXTURE"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun listEntriesReturnsEmptyListWhenAccountHasNoEntries() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "entries" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun aHeldButUnsettledPaymentDoesNotAppearInTheEntries() = malaiAcceptanceTest {
        val fundingAccountId = randomUUID()
        given(thereIsNoLedgerData())
        and(aBalancedEntryPair(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            amountMinorUnits = 10_000L,
            postedAt = Instant.parse("2026-05-01T10:00:00Z"),
        ))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "entries" To listOf(
                    json {
                        "entryId" To something
                        "transactionId" To something
                        "postedAt" To "2026-05-01T10:00:00Z"
                        "amountMinorUnits" To 10000
                        "runningBalanceMinorUnits" To 10000
                        "transactionType" To "FIXTURE"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun listEntriesForAPeriodReturnsOpeningClosingAndInPeriodEntries() = malaiAcceptanceTest {
        val fundingAccountId = randomUUID()
        given(thereIsNoLedgerData())
        and(aBalancedEntryPair(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            amountMinorUnits = 20_000L,
            postedAt = Instant.parse("2026-04-30T09:00:00Z"),
        ))
        and(aBalancedEntryPair(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            amountMinorUnits = 10_000L,
            postedAt = Instant.parse("2026-05-01T10:00:00Z"),
        ))
        and(aBalancedEntryPair(
            fromAccountId = someAccountUid(),
            toAccountId = fundingAccountId,
            amountMinorUnits = 2_500L,
            postedAt = Instant.parse("2026-05-15T11:00:00Z"),
            type = "PAYMENT",
        ))
        and(aBalancedEntryPair(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            amountMinorUnits = 500L,
            postedAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))

        whenever(ledgerServiceReceives(
            get()
                .url("/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
                .queryParam("periodStart", "2026-05-01T00:00:00Z")
                .queryParam("periodEnd", "2026-06-01T00:00:00Z")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "openingBalanceMinorUnits" To 20000
                "closingBalanceMinorUnits" To 27500
                "entries" To listOf(
                    json {
                        "entryId" To something
                        "transactionId" To something
                        "postedAt" To "2026-05-15T11:00:00Z"
                        "amountMinorUnits" To -2500
                        "runningBalanceMinorUnits" To 27500
                        "transactionType" To "PAYMENT"
                        noExtraProps()
                    },
                    json {
                        "entryId" To something
                        "transactionId" To something
                        "postedAt" To "2026-05-01T10:00:00Z"
                        "amountMinorUnits" To 10000
                        "runningBalanceMinorUnits" To 30000
                        "transactionType" To "FIXTURE"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun listEntriesCapsAtTheDefaultLimitAndPreservesRunningBalanceFromClippedHistory() = malaiAcceptanceTest {
        val fundingAccountId = randomUUID()
        given(thereIsNoLedgerData())
        and(manyBalancedEntryPairs(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            count = 5,
            amountMinorUnitsEach = 1L,
            firstPostedAt = Instant.parse("2026-05-01T10:00:00Z"),
        ))
        and(manyBalancedEntryPairs(
            fromAccountId = fundingAccountId,
            toAccountId = someAccountUid(),
            count = 100,
            amountMinorUnitsEach = 10L,
            firstPostedAt = Instant.parse("2026-05-01T10:00:05Z"),
        ))

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "entries" To expectedCappedEntries()
                noExtraProps()
            }
        }
    }

    private fun expectedCappedEntries() = (0 until 100).map { i ->
        json {
            "entryId" To something
            "transactionId" To something
            "postedAt" To Instant.parse("2026-05-01T10:00:05Z").plusSeconds((99 - i).toLong()).toString()
            "amountMinorUnits" To 10
            "runningBalanceMinorUnits" To (1005 - (i * 10))
            "transactionType" To "FIXTURE"
            noExtraProps()
        }
    }
}
