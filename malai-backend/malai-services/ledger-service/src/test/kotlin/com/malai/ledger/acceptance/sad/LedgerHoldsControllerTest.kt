package com.malai.ledger.acceptance.sad

import com.malai.commons.model.Currency.GBP
import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.ledger.model.HoldStatus.PLACED
import com.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.ledger.model.LedgerAccountKind.SYSTEM
import com.malai.ledger.model.LedgerEntryRequest
import com.malai.ledger.model.LedgerGuardPolicy.ALLOW_NEGATIVE
import com.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.ledger.model.PlaceHoldRequest
import com.malai.ledger.model.PostTransactionRequest
import com.malai.ledger.model.ReleaseHoldRequest
import com.malai.ledger.model.SettleHoldRequest
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
import java.util.UUID

class LedgerHoldsControllerTest : LedgerAcceptanceTest() {

    private val someHoldId = UUID.fromString("00000000-0000-4000-7000-0000000000b1")
    private val anotherHoldId = UUID.fromString("00000000-0000-4000-7000-0000000000b2")

    @Test
    fun placeHoldBreachingAvailableBalanceIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 100L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PlaceHoldRequest(
                    holdId = someHoldId,
                    accountId = someAccountUid(),
                    amountMinorUnits = 2_500L,
                    placedAt = "2026-10-15T10:30:00Z",
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7977"
            }
        }

        and(theHoldsForAccount(someAccountUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun placeHoldBreachingAFlooredAccountsFloorIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, FLOORED, floorMinorUnits = -1_000L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PlaceHoldRequest(
                    holdId = someHoldId,
                    accountId = someAccountUid(),
                    amountMinorUnits = 1_001L,
                    placedAt = "2026-10-15T10:30:00Z",
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7977"
            }
        }

        and(theHoldsForAccount(someAccountUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun aSecondHoldCannotReserveAlreadyHeldMoney() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 2_500L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PlaceHoldRequest(
                    holdId = anotherHoldId,
                    accountId = someAccountUid(),
                    amountMinorUnits = 2_500L,
                    placedAt = "2026-10-15T10:30:00Z",
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7977"
            }
        }

        and(theHoldsForAccount(someAccountUid())) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun placingAHoldOnAnUnregisteredAccountIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PlaceHoldRequest(
                    holdId = someHoldId,
                    accountId = someAccountUid(),
                    amountMinorUnits = 2_500L,
                    placedAt = "2026-10-15T10:30:00Z",
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7976"
            }
        }

        and(theHoldsForAccount(someAccountUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun settlingAnUnknownHoldIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds/$someHoldId/settle")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "PAYMENT_SETTLEMENT",
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
                "message" To "7974"
            }
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun settlingForMoreThanTheHeldAmountIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, SYSTEM, ALLOW_NEGATIVE))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds/$someHoldId/settle")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(SettleHoldRequest(
                    transactionId = someTransferUid(),
                    type = "CARD_PRESENTMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    amountMinorUnits = 5_000L,
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -5_000L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), 5_000L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7968"
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this.status shouldBe PLACED
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun settlingForANonPositiveAmountIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, SYSTEM, ALLOW_NEGATIVE))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds/$someHoldId/settle")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(SettleHoldRequest(
                    transactionId = someTransferUid(),
                    type = "CARD_PRESENTMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    amountMinorUnits = 0L,
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -2_500L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), 2_500L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7968"
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this.status shouldBe PLACED
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun releasingAnUnknownHoldIsRejected() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds/$someHoldId/release")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(ReleaseHoldRequest(resolvedAt = "2026-10-16T09:00:00Z")))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7974"
            }
        }
    }

    @Test
    fun readingAnUnknownHoldIsNotFound() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(get().url("/api/v1/internal/ledger/holds/$someHoldId")))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "7974"
                noExtraProps()
            }
        }
    }
}
