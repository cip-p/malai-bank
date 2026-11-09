package com.malai.ledger.acceptance.happy

import com.malai.commons.model.Currency.GBP
import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.ledger.entity.HoldEntity
import com.malai.ledger.model.HoldStatus.PLACED
import com.malai.ledger.model.HoldStatus.RELEASED
import com.malai.ledger.model.HoldStatus.SETTLED
import com.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.ledger.model.LedgerAccountKind.SYSTEM
import com.malai.ledger.model.LedgerEntryRequest
import com.malai.ledger.model.LedgerGuardPolicy.ALLOW_NEGATIVE
import com.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.ledger.model.BalancesResponse
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
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class LedgerHoldsControllerTest : LedgerAcceptanceTest() {

    private val someHoldId = UUID.fromString("00000000-0000-4000-7000-0000000000a1")
    private val schemeSettlementId = UUID.fromString("00000000-0000-4000-8001-000000010002")
    private val firstEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000e1")
    private val secondEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000e2")

    @Test
    fun placeHoldReservesFundsAndPersistsAPlacedHold() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))

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
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "status" To "PLACED"
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this shouldBe HoldEntity(
                holdId = someHoldId,
                accountId = someAccountUid(),
                amountMinorUnits = 2_500L,
                status = PLACED,
                placedAt = Instant.parse("2026-10-15T10:30:00Z"),
                resolvedAt = null,
            )
        }
    }

    @Test
    fun placeHoldLandingExactlyOnAFlooredAccountsFloorIsAccepted() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, FLOORED, floorMinorUnits = -1_000L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PlaceHoldRequest(
                    holdId = someHoldId,
                    accountId = someAccountUid(),
                    amountMinorUnits = 1_000L,
                    placedAt = "2026-10-15T10:30:00Z",
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 1_000
                "status" To "PLACED"
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this shouldBe HoldEntity(
                holdId = someHoldId,
                accountId = someAccountUid(),
                amountMinorUnits = 1_000L,
                status = PLACED,
                placedAt = Instant.parse("2026-10-15T10:30:00Z"),
                resolvedAt = null,
            )
        }

        and(theBalancesForAccount(someAccountUid())) {
            this shouldBe BalancesResponse(someAccountUid(), 0L, 1_000L, -1_000L)
        }
    }

    @Test
    fun replayingTheSamePlaceHoldIsANoOp() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))

        val requestBody = testingObjectMapper.writeValueAsString(PlaceHoldRequest(
            holdId = someHoldId,
            accountId = someAccountUid(),
            amountMinorUnits = 2_500L,
            placedAt = "2026-10-15T10:30:00Z",
        ))

        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds").header("Content-Type", "application/json").body(requestBody)
        ))
        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds").header("Content-Type", "application/json").body(requestBody)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "status" To "PLACED"
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(theHoldsForAccount(someAccountUid())) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun settleHoldMarksItSettledAndRecordsTheBalancedDebit() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(schemeSettlementId, GBP, SYSTEM, ALLOW_NEGATIVE))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

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
                        LedgerEntryRequest(someAccountUid(), -2_500L, counterpartyId = schemeSettlementId),
                        LedgerEntryRequest(schemeSettlementId, 2_500L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "status" To "SETTLED"
                "resolvedAt" To "2026-10-15T10:30:00Z"
                "transaction" To json {
                    "transactionId" To someTransferUid().toString()
                    "postedAt" To "2026-10-15T10:30:00Z"
                    "entries" To listOf(
                        json {
                            "entryId" To firstEntryId.toString()
                            "accountId" To someAccountUid().toString()
                            "amountMinorUnits" To -2_500
                            noExtraProps()
                        },
                        json {
                            "entryId" To secondEntryId.toString()
                            "accountId" To schemeSettlementId.toString()
                            "amountMinorUnits" To 2_500
                            noExtraProps()
                        },
                    )
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this shouldBe HoldEntity(
                holdId = someHoldId,
                accountId = someAccountUid(),
                amountMinorUnits = 2_500L,
                status = SETTLED,
                placedAt = Instant.parse("2026-10-15T10:30:00Z"),
                resolvedAt = Instant.parse("2026-10-15T10:30:00Z"),
            )
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 2
        }
    }

    @Test
    fun partialSettleSettlesTheAmountReleasesTheRemainderAndDropsAvailableToLedger() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(schemeSettlementId, GBP, SYSTEM, ALLOW_NEGATIVE))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds/$someHoldId/settle")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(SettleHoldRequest(
                    transactionId = someTransferUid(),
                    type = "CARD_PRESENTMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    amountMinorUnits = 1_000L,
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -1_000L, counterpartyId = schemeSettlementId),
                        LedgerEntryRequest(schemeSettlementId, 1_000L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "status" To "SETTLED"
                "resolvedAt" To "2026-10-15T10:30:00Z"
                "transaction" To json {
                    "transactionId" To someTransferUid().toString()
                    "postedAt" To "2026-10-15T10:30:00Z"
                    "entries" To listOf(
                        json {
                            "entryId" To firstEntryId.toString()
                            "accountId" To someAccountUid().toString()
                            "amountMinorUnits" To -1_000
                            noExtraProps()
                        },
                        json {
                            "entryId" To secondEntryId.toString()
                            "accountId" To schemeSettlementId.toString()
                            "amountMinorUnits" To 1_000
                            noExtraProps()
                        },
                    )
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this shouldBe HoldEntity(
                holdId = someHoldId,
                accountId = someAccountUid(),
                amountMinorUnits = 2_500L,
                status = SETTLED,
                placedAt = Instant.parse("2026-10-15T10:30:00Z"),
                resolvedAt = Instant.parse("2026-10-15T10:30:00Z"),
            )
        }

        and(theBalancesForAccount(someAccountUid())) {
            this shouldBe BalancesResponse(
                accountId = someAccountUid(),
                ledgerBalanceMinorUnits = 9_000L,
                heldAmountMinorUnits = 0L,
                availableBalanceMinorUnits = 9_000L,
            )
        }
    }

    @Test
    fun replayingTheSameSettleHoldIsANoOp() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(schemeSettlementId, GBP, SYSTEM, ALLOW_NEGATIVE))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

        val requestBody = testingObjectMapper.writeValueAsString(PostTransactionRequest(
            transactionId = someTransferUid(),
            type = "PAYMENT_SETTLEMENT",
            initiatedById = someAccountHolderUid(),
            postedAt = "2026-10-15T10:30:00Z",
            entries = listOf(
                LedgerEntryRequest(someAccountUid(), -2_500L, counterpartyId = schemeSettlementId),
                LedgerEntryRequest(schemeSettlementId, 2_500L, counterpartyId = someAccountUid()),
            ),
        ))

        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds/$someHoldId/settle").header("Content-Type", "application/json").body(requestBody)
        ))
        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds/$someHoldId/settle").header("Content-Type", "application/json").body(requestBody)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this.status shouldBe SETTLED
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 2
        }
    }

    @Test
    fun replayingTheSamePartialSettleIsANoOp() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(schemeSettlementId, GBP, SYSTEM, ALLOW_NEGATIVE))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

        val requestBody = testingObjectMapper.writeValueAsString(SettleHoldRequest(
            transactionId = someTransferUid(),
            type = "CARD_PRESENTMENT",
            initiatedById = someAccountHolderUid(),
            postedAt = "2026-10-15T10:30:00Z",
            amountMinorUnits = 1_000L,
            entries = listOf(
                LedgerEntryRequest(someAccountUid(), -1_000L, counterpartyId = schemeSettlementId),
                LedgerEntryRequest(schemeSettlementId, 1_000L, counterpartyId = someAccountUid()),
            ),
        ))

        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds/$someHoldId/settle").header("Content-Type", "application/json").body(requestBody)
        ))
        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds/$someHoldId/settle").header("Content-Type", "application/json").body(requestBody)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "status" To "SETTLED"
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this.status shouldBe SETTLED
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 2
        }

        and(theBalancesForAccount(someAccountUid())) {
            this shouldBe BalancesResponse(
                accountId = someAccountUid(),
                ledgerBalanceMinorUnits = 9_000L,
                heldAmountMinorUnits = 0L,
                availableBalanceMinorUnits = 9_000L,
            )
        }
    }

    @Test
    fun releaseHoldFreesTheReservationAndRecordsNoEntries() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/holds/$someHoldId/release")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(ReleaseHoldRequest(resolvedAt = "2026-10-16T09:00:00Z")))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "status" To "RELEASED"
                "placedAt" To "2026-10-15T10:30:00Z"
                "resolvedAt" To "2026-10-16T09:00:00Z"
                noExtraProps()
            }
        }

        and(theHoldInTheDatabase(someHoldId)) {
            this shouldBe HoldEntity(
                holdId = someHoldId,
                accountId = someAccountUid(),
                amountMinorUnits = 2_500L,
                status = RELEASED,
                placedAt = Instant.parse("2026-10-15T10:30:00Z"),
                resolvedAt = Instant.parse("2026-10-16T09:00:00Z"),
            )
        }

        and(theEntriesInTheDatabase()) {
            this shouldHaveSize 2
        }
    }

    @Test
    fun replayingTheSameReleaseHoldIsANoOp() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        val requestBody = testingObjectMapper.writeValueAsString(ReleaseHoldRequest(resolvedAt = "2026-10-16T09:00:00Z"))

        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds/$someHoldId/release").header("Content-Type", "application/json").body(requestBody)
        ))
        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/holds/$someHoldId/release").header("Content-Type", "application/json").body(requestBody)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "status" To "RELEASED"
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "placedAt" To "2026-10-15T10:30:00Z"
                "resolvedAt" To "2026-10-16T09:00:00Z"
                noExtraProps()
            }
        }

        and(theEntriesInTheDatabase()) {
            this shouldHaveSize 2
        }
    }

    @Test
    fun aPlacedHoldReadsBackAsStillReservingTheFunds() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(holdId = someHoldId, accountId = someAccountUid(), amountMinorUnits = 2_500L))

        whenever(ledgerServiceReceives(get().url("/api/v1/internal/ledger/holds/$someHoldId")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "status" To "PLACED"
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }
    }

    @Test
    fun aReleasedHoldReadsBackWithTheMomentItWasResolved() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(aHoldExists(
            holdId = someHoldId,
            accountId = someAccountUid(),
            amountMinorUnits = 2_500L,
            status = RELEASED,
            resolvedAt = Instant.parse("2026-10-16T09:00:00Z"),
        ))

        whenever(ledgerServiceReceives(get().url("/api/v1/internal/ledger/holds/$someHoldId")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "holdId" To someHoldId.toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "status" To "RELEASED"
                "placedAt" To "2026-10-15T10:30:00Z"
                "resolvedAt" To "2026-10-16T09:00:00Z"
                noExtraProps()
            }
        }
    }
}
