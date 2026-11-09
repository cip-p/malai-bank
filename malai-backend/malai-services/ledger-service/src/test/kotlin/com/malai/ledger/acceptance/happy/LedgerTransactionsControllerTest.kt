package com.malai.ledger.acceptance.happy

import com.malai.commons.model.Currency.GBP
import com.malai.ledger.acceptance.LedgerAcceptanceTest
import com.malai.ledger.entity.LedgerEntryRowEntity
import com.malai.ledger.entity.LedgerTransactionEntity
import com.malai.ledger.model.BalancesResponse
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
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class LedgerTransactionsControllerTest : LedgerAcceptanceTest() {

    private val firstEntryId = UUID.fromString("00000000-0000-4000-7000-000000000001")
    private val secondEntryId = UUID.fromString("00000000-0000-4000-7000-000000000002")
    private val creditTransferUid = UUID.fromString("00000000-0000-4000-7000-000000000003")
    private val someTraceId = "some-trace-id"

    @Test
    fun postBalancedTransactionRecordsTransactionAndEntries() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/transactions")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "PAYMENT",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    description = "savings top-up",
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), -2_500L, counterpartyId = anotherAccountUid(), description = "savings top-up"),
                        LedgerEntryRequest(anotherAccountUid(), 2_500L, counterpartyId = someAccountUid(), description = "savings top-up"),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
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
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 2_500
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theTransactionInTheDatabase(someTransferUid())) {
            this shouldBe LedgerTransactionEntity(
                transactionId = someTransferUid(),
                initiatedById = someAccountHolderUid(),
                initiatedAt = Instant.parse("2026-10-15T10:30:00Z"),
                type = "PAYMENT",
                description = "savings top-up",
                traceId = null,
            )
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldContainExactlyInAnyOrder listOf(
                LedgerEntryRowEntity(
                    entryId = firstEntryId,
                    transactionId = someTransferUid(),
                    accountId = someAccountUid(),
                    amountMinorUnits = -2_500L,
                    postedAt = Instant.parse("2026-10-15T10:30:00Z"),
                    description = "savings top-up",
                    counterpartyId = anotherAccountUid(),
                    merchantName = null,
                    category = null,
                    traceId = null,
                ),
                LedgerEntryRowEntity(
                    entryId = secondEntryId,
                    transactionId = someTransferUid(),
                    accountId = anotherAccountUid(),
                    amountMinorUnits = 2_500L,
                    postedAt = Instant.parse("2026-10-15T10:30:00Z"),
                    description = "savings top-up",
                    counterpartyId = someAccountUid(),
                    merchantName = null,
                    category = null,
                    traceId = null,
                ),
            )
        }
    }

    @Test
    fun debitLandingExactlyOnAFlooredAccountsFloorIsAccepted() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, FLOORED, floorMinorUnits = -1_000L))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, ALLOW_NEGATIVE))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

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
                        LedgerEntryRequest(someAccountUid(), -1_000L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), 1_000L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
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
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 1_000
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theBalancesForAccount(someAccountUid())) {
            this shouldBe BalancesResponse(someAccountUid(), -1_000L, 0L, -1_000L)
        }
    }

    @Test
    fun aCreditToAFlooredAccountIsAlwaysAcceptedAndMayLeaveItPositive() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, FLOORED, floorMinorUnits = -1_000L))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, ALLOW_NEGATIVE))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

        whenever(ledgerServiceReceives(
            post()
                .url("/api/v1/internal/ledger/transactions")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(PostTransactionRequest(
                    transactionId = someTransferUid(),
                    type = "CARD_REFUND",
                    initiatedById = someAccountHolderUid(),
                    postedAt = "2026-10-15T10:30:00Z",
                    entries = listOf(
                        LedgerEntryRequest(someAccountUid(), 5_000L, counterpartyId = anotherAccountUid()),
                        LedgerEntryRequest(anotherAccountUid(), -5_000L, counterpartyId = someAccountUid()),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theBalancesForAccount(someAccountUid())) {
            this shouldBe BalancesResponse(someAccountUid(), 5_000L, 0L, 5_000L)
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 2
        }
    }

    @Test
    fun replayingTheSameTransactionIdIsANoOp() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aLedgerAccountIsRegistered(someAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aLedgerAccountIsRegistered(anotherAccountUid(), GBP, CUSTOMER, NO_OVERDRAFT))
        and(aSettledBalanceExists(accountId = someAccountUid(), amountMinorUnits = 10_000L))
        and(theNextGeneratedUuidWillBe(firstEntryId, secondEntryId))

        val requestBody = testingObjectMapper.writeValueAsString(PostTransactionRequest(
            transactionId = someTransferUid(),
            type = "PAYMENT",
            initiatedById = someAccountHolderUid(),
            postedAt = "2026-10-15T10:30:00Z",
            entries = listOf(
                LedgerEntryRequest(someAccountUid(), -2_500L, counterpartyId = anotherAccountUid()),
                LedgerEntryRequest(anotherAccountUid(), 2_500L, counterpartyId = someAccountUid()),
            ),
        ))

        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/transactions").header("Content-Type", "application/json").body(requestBody)
        ))
        whenever(ledgerServiceReceives(
            post().url("/api/v1/internal/ledger/transactions").header("Content-Type", "application/json").body(requestBody)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
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
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 2_500
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theEntriesForTransaction(someTransferUid())) {
            this shouldHaveSize 2
        }
    }

    @Test
    fun listTransactionEntriesReturnsBothLegsWithTheTransactionsPostedMomentTypeAndDescription() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aBalancedEntryPair(
            fromAccountId = anotherAccountUid(),
            toAccountId = someAccountUid(),
            amountMinorUnits = 10_000L,
            postedAt = Instant.parse("2026-05-01T10:00:00Z"),
        ))
        and(aBalancedEntryPair(
            fromAccountId = someAccountUid(),
            toAccountId = anotherAccountUid(),
            amountMinorUnits = 2_500L,
            postedAt = Instant.parse("2026-05-03T12:00:00Z"),
            transactionId = someTransferUid(),
            description = "rent",
            counterpartyId = someCounterpartyId(),
            merchantName = "some-merchant-name",
            category = "some-category",
            type = "PAYMENT",
        ))

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/transactions/${someTransferUid()}/entries")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "postedAt" To "2026-05-03T12:00:00Z"
                "type" To "PAYMENT"
                "description" To "rent"
                "entries" To listOf(
                    json {
                        "entryId" To something
                        "accountId" To someAccountUid().toString()
                        "transactionId" To someTransferUid().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "amountMinorUnits" To -2_500
                        "runningBalanceMinorUnits" To 7_500
                        "description" To "rent"
                        "counterpartyId" To someCounterpartyId().toString()
                        "merchantName" To "some-merchant-name"
                        "category" To "some-category"
                        "transactionType" To "PAYMENT"
                        noExtraProps()
                    },
                    json {
                        "entryId" To something
                        "accountId" To anotherAccountUid().toString()
                        "transactionId" To someTransferUid().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "amountMinorUnits" To 2_500
                        "runningBalanceMinorUnits" To -7_500
                        "description" To "rent"
                        "counterpartyId" To someCounterpartyId().toString()
                        "merchantName" To "some-merchant-name"
                        "category" To "some-category"
                        "transactionType" To "PAYMENT"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun listTransactionsForATraceReturnsEveryTransactionThatTraceCausedInPostedOrder() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())
        and(aBalancedEntryPair(
            fromAccountId = someAccountUid(),
            toAccountId = anotherAccountUid(),
            amountMinorUnits = 2_500L,
            postedAt = Instant.parse("2026-05-03T12:00:00Z"),
            transactionId = someTransferUid(),
            description = "rent",
            type = "PAYMENT_SETTLEMENT",
            traceId = someTraceId,
        ))
        and(aBalancedEntryPair(
            fromAccountId = anotherAccountUid(),
            toAccountId = someAccountUid(),
            amountMinorUnits = 2_500L,
            postedAt = Instant.parse("2026-05-03T12:00:01Z"),
            transactionId = creditTransferUid,
            description = "rent",
            type = "PAYMENT_CREDIT",
            traceId = someTraceId,
        ))
        and(aBalancedEntryPair(
            fromAccountId = someAccountUid(),
            toAccountId = anotherAccountUid(),
            amountMinorUnits = 9_900L,
            postedAt = Instant.parse("2026-05-04T12:00:00Z"),
            traceId = "some-other-trace-id",
        ))

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/transactions").queryParam("traceId", someTraceId)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "traceId" To someTraceId
                "transactions" To listOf(
                    json {
                        "transactionId" To someTransferUid().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "type" To "PAYMENT_SETTLEMENT"
                        "description" To "rent"
                        "entries" To listOf(
                            json {
                                "entryId" To something
                                "accountId" To someAccountUid().toString()
                                "transactionId" To someTransferUid().toString()
                                "postedAt" To "2026-05-03T12:00:00Z"
                                "amountMinorUnits" To -2_500
                                "runningBalanceMinorUnits" To -2_500
                                "description" To "rent"
                                "transactionType" To "PAYMENT_SETTLEMENT"
                                noExtraProps()
                            },
                            json {
                                "entryId" To something
                                "accountId" To anotherAccountUid().toString()
                                "transactionId" To someTransferUid().toString()
                                "postedAt" To "2026-05-03T12:00:00Z"
                                "amountMinorUnits" To 2_500
                                "runningBalanceMinorUnits" To 2_500
                                "description" To "rent"
                                "transactionType" To "PAYMENT_SETTLEMENT"
                                noExtraProps()
                            },
                        )
                        noExtraProps()
                    },
                    json {
                        "transactionId" To creditTransferUid.toString()
                        "postedAt" To "2026-05-03T12:00:01Z"
                        "type" To "PAYMENT_CREDIT"
                        "description" To "rent"
                        "entries" To listOf(
                            json {
                                "entryId" To something
                                "accountId" To anotherAccountUid().toString()
                                "transactionId" To creditTransferUid.toString()
                                "postedAt" To "2026-05-03T12:00:01Z"
                                "amountMinorUnits" To -2_500
                                "runningBalanceMinorUnits" To 0
                                "description" To "rent"
                                "transactionType" To "PAYMENT_CREDIT"
                                noExtraProps()
                            },
                            json {
                                "entryId" To something
                                "accountId" To someAccountUid().toString()
                                "transactionId" To creditTransferUid.toString()
                                "postedAt" To "2026-05-03T12:00:01Z"
                                "amountMinorUnits" To 2_500
                                "runningBalanceMinorUnits" To 0
                                "description" To "rent"
                                "transactionType" To "PAYMENT_CREDIT"
                                noExtraProps()
                            },
                        )
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun listTransactionsForAnUnknownTraceReturnsNoTransactions() = malaiAcceptanceTest {
        given(thereIsNoLedgerData())

        whenever(ledgerServiceReceives(
            get().url("/api/v1/internal/ledger/transactions")
                .queryParam("traceId", "a-trace-that-caused-nothing")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "traceId" To "a-trace-that-caused-nothing"
                "transactions" To emptyList<Any>()
                noExtraProps()
            }
        }
    }
}
