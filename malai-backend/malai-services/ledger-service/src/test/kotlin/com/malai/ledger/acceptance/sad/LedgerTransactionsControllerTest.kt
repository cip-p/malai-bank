package com.malai.ledger.acceptance.sad

import com.malai.commons.model.Currency.GBP
import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.ledger.model.LedgerEntryRequest
import com.malai.ledger.model.LedgerGuardPolicy.ALLOW_NEGATIVE
import com.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.ledger.model.PostTransactionRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT

class LedgerTransactionsControllerTest : LedgerAcceptanceTest() {

    private val someHoldId = java.util.UUID.fromString("00000000-0000-4000-7000-0000000000c1")

    @Test
    fun debitBreachingAvailableBalanceIsRejectedWithInsufficientFunds() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 100L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/transactions")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "PAYMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -2_500L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), 2_500L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7977"
            }
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun debitBelowAFlooredAccountsFloorIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, FLOORED, floorMinorUnits = -1_000L))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, ALLOW_NEGATIVE))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/transactions")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "CARD_PRESENTMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -1_001L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), 1_001L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7977"
            }
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun aDebitIsRejectedWhenAnActiveHoldHasConsumedTheFloorHeadroom() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, FLOORED, floorMinorUnits = -1_000L))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, ALLOW_NEGATIVE))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 500L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 1_500L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/transactions")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "CARD_PRESENTMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -1L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), 1L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7977"
            }
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun unbalancedTransactionIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/transactions")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "PAYMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -2_500L),
                        LedgerEntryRequest(anotherAccountUid(), 2_000L),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7975"
            }
        }

        and(theTransactionsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postingToAnUnregisteredAccountIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/transactions")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "PAYMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -2_500L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), 2_500L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7976"
            }
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun listingTheEntriesOfAnUnknownTransactionIsNotFound() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/transactions/${someTransferUid()}/entries")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8976"
                noExtraProps()
            }
        }
    }
}
