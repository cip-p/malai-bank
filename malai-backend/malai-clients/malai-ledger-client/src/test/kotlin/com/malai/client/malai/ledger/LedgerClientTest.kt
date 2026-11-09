package com.malai.client.malai.ledger

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder.Companion.aBalancesResponse
import com.malai.client.malai.ledger.builders.response.EntriesResponseBuilder.Companion.anEntriesResponse
import com.malai.client.malai.ledger.builders.response.EntriesResponseBuilder.EntryBuilder.Companion.anEntry
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.PostedEntryBuilder.Companion.aPostedEntry
import com.malai.client.malai.ledger.builders.response.RegisterLedgerAccountResponseBuilder.Companion.aRegisterLedgerAccountResponse
import com.malai.client.malai.ledger.builders.response.SettleHoldResponseBuilder.Companion.aSettleHoldResponse
import com.malai.client.malai.ledger.builders.response.TracedTransactionsResponseBuilder.Companion.aTracedTransactionsResponse
import com.malai.client.malai.ledger.builders.response.TransactionEntriesResponseBuilder.Companion.aTransactionEntriesResponse
import com.malai.client.malai.ledger.builders.response.TransactionEntriesResponseBuilder.TransactionEntryBuilder.Companion.aTransactionEntry
import com.malai.client.malai.ledger.model.BalancesResponse
import com.malai.client.malai.ledger.model.EntriesResponse
import com.malai.client.malai.ledger.model.HoldResponse
import com.malai.client.malai.ledger.model.LedgerEntryProjection
import com.malai.client.malai.ledger.model.HoldStatus.PLACED
import com.malai.client.malai.ledger.model.HoldStatus.RELEASED
import com.malai.client.malai.ledger.model.HoldStatus.SETTLED
import com.malai.client.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.client.malai.ledger.model.LedgerEntryRequest
import com.malai.client.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.client.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.client.malai.ledger.model.PlaceHoldRequest
import com.malai.client.malai.ledger.model.PostTransactionRequest
import com.malai.client.malai.ledger.model.PostTransactionResponse
import com.malai.client.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.client.malai.ledger.model.RegisterLedgerAccountResponse
import com.malai.client.malai.ledger.model.ReleaseHoldRequest
import com.malai.client.malai.ledger.model.SettleHoldResponse
import com.malai.client.malai.ledger.model.TracedTransactionsResponse
import com.malai.client.malai.ledger.model.TransactionEntriesResponse
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import java.util.UUID

@MalaiClientSpringTest
class LedgerClientTest : MalaiClientTest(), WithLedger {

    @Autowired
    private lateinit var ledgerClient: LedgerClient

    private var lastRegisterResponse: RegisterLedgerAccountResponse? = null
    private var lastPostResponse: PostTransactionResponse? = null
    private var lastHoldResponse: HoldResponse? = null
    private var lastSettleResponse: SettleHoldResponse? = null
    private var lastBalancesResponse: BalancesResponse? = null
    private var lastEntriesResponse: EntriesResponse? = null
    private var lastTransactionEntriesResponse: TransactionEntriesResponse? = null
    private var lastTracedTransactionsResponse: TracedTransactionsResponse? = null
    private var lastThrowable: Throwable? = null

    private val someHoldId = UUID.fromString("00000000-0000-4000-7000-0000000000d1")
    private val someEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")
    private val anotherEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000f2")

    @Test
    fun registerLedgerAccount() {
        given(registerLedgerAccountReturns(aRegisterLedgerAccountResponse()
            .withAccountId(someAccountUid())
            .withCurrency("GBP")
            .withKind("CUSTOMER")
            .withGuardPolicy("NO_OVERDRAFT")))

        whenever(clientCallsRegisterLedgerAccount(
            "some-service-token",
            RegisterLedgerAccountRequest(
                accountId = someAccountUid(),
                currency = "GBP",
                kind = CUSTOMER,
                guardPolicy = NO_OVERDRAFT,
            )
        ))

        then(theLastRegisterResponse()) {
            this shouldBe RegisterLedgerAccountResponse(someAccountUid(), "GBP", CUSTOMER, NO_OVERDRAFT)
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "currency" To "GBP"
                "kind" To "CUSTOMER"
                "guardPolicy" To "NO_OVERDRAFT"
                noExtraProps()
            }
        }
    }

    @Test
    fun registerLedgerAccountWithAFloor() {
        given(registerLedgerAccountReturns(aRegisterLedgerAccountResponse()
            .withAccountId(someAccountUid())
            .withCurrency("GBP")
            .withKind("CUSTOMER")
            .withGuardPolicy("FLOORED")
            .withFloorMinorUnits(-250_000L)))

        whenever(clientCallsRegisterLedgerAccount(
            "some-service-token",
            RegisterLedgerAccountRequest(
                accountId = someAccountUid(),
                currency = "GBP",
                kind = CUSTOMER,
                guardPolicy = FLOORED,
                floorMinorUnits = -250_000L,
            )
        ))

        then(theLastRegisterResponse()) {
            this shouldBe RegisterLedgerAccountResponse(someAccountUid(), "GBP", CUSTOMER, FLOORED, -250_000L)
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "currency" To "GBP"
                "kind" To "CUSTOMER"
                "guardPolicy" To "FLOORED"
                "floorMinorUnits" To -250_000
                noExtraProps()
            }
        }
    }

    @Test
    fun postTransaction() {
        given(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")
            .withEntries(
                aPostedEntry()
                    .withEntryId(UUID.randomUUID())
                    .withAccountId(someAccountUid())
                    .withAmountMinorUnits(-2_500L),
                aPostedEntry()
                    .withEntryId(UUID.randomUUID())
                    .withAccountId(anotherAccountUid())
                    .withAmountMinorUnits(2_500L),
            )))

        whenever(clientCallsPostTransaction(
            "some-service-token",
            PostTransactionRequest(
                transactionId = someTransferUid(),
                type = "PAYMENT",
                initiatedById = someAccountHolderUid(),
                postedAt = "2026-10-15T10:30:00Z",
                description = "savings top-up",
                traceId = "trace-1",
                entries = listOf(
                    LedgerEntryRequest(accountId = someAccountUid(), amountMinorUnits = -2_500L, counterpartyId = anotherAccountUid()),
                    LedgerEntryRequest(accountId = anotherAccountUid(), amountMinorUnits = 2_500L, counterpartyId = someAccountUid()),
                ),
            )
        ))

        then(theLastPostResponse()) {
            this.transactionId shouldBe someTransferUid()
            this.postedAt shouldBe "2026-10-15T10:30:00Z"
            this.entries.size shouldBe 2
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "type" To "PAYMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "description" To "savings top-up"
                "traceId" To "trace-1"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -2_500
                        "counterpartyId" To anotherAccountUid().toString()
                        noExtraProps()
                    },
                    json {
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 2_500
                        "counterpartyId" To someAccountUid().toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun postTransactionRejectedForInsufficientFunds() {
        given(postTransactionReturnsInsufficientFunds())

        whenever(clientCallsPostTransactionCapturingError(
            "some-service-token",
            PostTransactionRequest(
                transactionId = someTransferUid(),
                type = "PAYMENT",
                initiatedById = someAccountHolderUid(),
                postedAt = "2026-10-15T10:30:00Z",
                entries = listOf(
                    LedgerEntryRequest(accountId = someAccountUid(), amountMinorUnits = -2_500L),
                    LedgerEntryRequest(accountId = anotherAccountUid(), amountMinorUnits = 2_500L),
                ),
            )
        ))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<LedgerInsufficientFundsException>()
        }
    }

    @Test
    fun placeHold() {
        given(placeHoldReturns(aHoldResponse()
            .withHoldId(someHoldId)
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(2_500L)
            .withStatus("PLACED")
            .withPlacedAt("2026-10-15T10:30:00Z")))

        whenever(clientCallsPlaceHold(
            "some-service-token",
            PlaceHoldRequest(
                holdId = someHoldId,
                accountId = someAccountUid(),
                amountMinorUnits = 2_500L,
                placedAt = "2026-10-15T10:30:00Z",
            )
        ))

        then(theLastHoldResponse()) {
            this shouldBe HoldResponse(someHoldId, someAccountUid(), 2_500L, PLACED, "2026-10-15T10:30:00Z", null)
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            body containsProp json {
                "holdId" To someHoldId.toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }
    }

    @Test
    fun placeHoldRejectedForInsufficientFunds() {
        given(placeHoldReturnsInsufficientFunds())

        whenever(clientCallsPlaceHoldCapturingError(
            "some-service-token",
            PlaceHoldRequest(
                holdId = someHoldId,
                accountId = someAccountUid(),
                amountMinorUnits = 2_500L,
                placedAt = "2026-10-15T10:30:00Z",
            )
        ))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<LedgerInsufficientFundsException>()
        }
    }

    @Test
    fun settleHold() {
        given(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(someHoldId)
            .withStatus("SETTLED")
            .withResolvedAt("2026-10-15T10:30:00Z")
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(someTransferUid())
                .withPostedAt("2026-10-15T10:30:00Z")
                .withEntries(
                    aPostedEntry().withEntryId(UUID.randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-2_500L),
                    aPostedEntry().withEntryId(UUID.randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(2_500L),
                ))))

        whenever(clientCallsSettleHold(
            "some-service-token",
            someHoldId,
            PostTransactionRequest(
                transactionId = someTransferUid(),
                type = "PAYMENT_SETTLEMENT",
                initiatedById = someAccountHolderUid(),
                postedAt = "2026-10-15T10:30:00Z",
                entries = listOf(
                    LedgerEntryRequest(accountId = someAccountUid(), amountMinorUnits = -2_500L, counterpartyId = anotherAccountUid()),
                    LedgerEntryRequest(accountId = anotherAccountUid(), amountMinorUnits = 2_500L, counterpartyId = someAccountUid()),
                ),
            )
        ))

        then(theLastSettleResponse()) {
            this.holdId shouldBe someHoldId
            this.status shouldBe SETTLED
            this.resolvedAt shouldBe "2026-10-15T10:30:00Z"
            this.transaction.transactionId shouldBe someTransferUid()
            this.transaction.entries.size shouldBe 2
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/$someHoldId/settle"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "type" To "PAYMENT_SETTLEMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -2_500
                        "counterpartyId" To anotherAccountUid().toString()
                        noExtraProps()
                    },
                    json {
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 2_500
                        "counterpartyId" To someAccountUid().toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun settleHoldForAPartialAmount() {
        given(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(someHoldId)
            .withStatus("SETTLED")
            .withResolvedAt("2026-10-15T10:30:00Z")
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(someTransferUid())
                .withPostedAt("2026-10-15T10:30:00Z")
                .withEntries(
                    aPostedEntry().withEntryId(UUID.randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-1_000L),
                    aPostedEntry().withEntryId(UUID.randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(1_000L),
                ))))

        whenever(clientCallsSettleHoldForAmount(
            "some-service-token",
            someHoldId,
            PostTransactionRequest(
                transactionId = someTransferUid(),
                type = "CARD_PRESENTMENT",
                initiatedById = someAccountHolderUid(),
                postedAt = "2026-10-15T10:30:00Z",
                entries = listOf(
                    LedgerEntryRequest(accountId = someAccountUid(), amountMinorUnits = -1_000L, counterpartyId = anotherAccountUid()),
                    LedgerEntryRequest(accountId = anotherAccountUid(), amountMinorUnits = 1_000L, counterpartyId = someAccountUid()),
                ),
            ),
            1_000L,
        ))

        then(theLastSettleResponse()) {
            this.holdId shouldBe someHoldId
            this.status shouldBe SETTLED
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/$someHoldId/settle"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "type" To "CARD_PRESENTMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "amountMinorUnits" To 1_000
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -1_000
                        "counterpartyId" To anotherAccountUid().toString()
                        noExtraProps()
                    },
                    json {
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 1_000
                        "counterpartyId" To someAccountUid().toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun releaseHold() {
        given(releaseHoldReturns(aHoldResponse()
            .withHoldId(someHoldId)
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(2_500L)
            .withStatus("RELEASED")
            .withPlacedAt("2026-10-15T10:30:00Z")
            .withResolvedAt("2026-10-16T09:00:00Z")))

        whenever(clientCallsReleaseHold(
            "some-service-token",
            someHoldId,
            ReleaseHoldRequest(resolvedAt = "2026-10-16T09:00:00Z"),
        ))

        then(theLastHoldResponse()) {
            this shouldBe HoldResponse(someHoldId, someAccountUid(), 2_500L, RELEASED, "2026-10-15T10:30:00Z", "2026-10-16T09:00:00Z")
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/$someHoldId/release"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            body containsProp json {
                "resolvedAt" To "2026-10-16T09:00:00Z"
                noExtraProps()
            }
        }
    }

    @Test
    fun getHoldReadsAStillPlacedReservationWhole() {
        given(getHoldReturns(someHoldId, aHoldResponse()
            .withHoldId(someHoldId)
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(2_500L)
            .withStatus("PLACED")
            .withPlacedAt("2026-10-15T10:30:00Z")))

        whenever(clientCallsGetHold("some-service-token", someHoldId))

        then(theLastHoldResponse()) {
            this shouldBe HoldResponse(someHoldId, someAccountUid(), 2_500L, PLACED, "2026-10-15T10:30:00Z", null)
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/$someHoldId"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
        }
    }

    @Test
    fun getBalances() {
        given(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withLedgerBalanceMinorUnits(10_000L)
            .withHeldAmountMinorUnits(2_500L)
            .withAvailableBalanceMinorUnits(7_500L)))

        whenever(clientCallsGetBalances("some-service-token", someAccountUid()))

        then(theLastBalancesResponse()) {
            this shouldBe BalancesResponse(someAccountUid(), 10_000L, 2_500L, 7_500L)
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/balances"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
        }
    }

    @Test
    fun listEntriesReturnsRecentEntries() {
        given(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(500L)
                    .withRunningBalanceMinorUnits(8_001L)
                    .withDescription("some-description")
                    .withCounterpartyId(anotherAccountUid())
                    .withMerchantName("some-merchant-name")
                    .withCategory("some-category")
                    .withTransactionType("DIRECT_DEBIT_COLLECTION"),
            )))

        whenever(clientCallsListEntries("some-service-token", someAccountUid()))

        then(theLastEntriesResponse()) {
            this.accountId shouldBe someAccountUid()
            this.entries shouldBe listOf(
                LedgerEntryProjection(
                    entryId = someEntryId,
                    transactionId = someTransferUid(),
                    postedAt = "2026-05-03T12:00:00Z",
                    amountMinorUnits = 500L,
                    runningBalanceMinorUnits = 8_001L,
                    description = "some-description",
                    counterpartyId = anotherAccountUid(),
                    merchantName = "some-merchant-name",
                    category = "some-category",
                    transactionType = "DIRECT_DEBIT_COLLECTION",
                )
            )
            this.openingBalanceMinorUnits shouldBe null
            this.closingBalanceMinorUnits shouldBe null
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/entries"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
        }
    }

    @Test
    fun listEntriesForAStatementPeriodCarriesOpeningAndClosing() {
        given(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withOpeningBalanceMinorUnits(20_000L)
            .withClosingBalanceMinorUnits(27_500L)
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-15T11:00:00Z")
                    .withAmountMinorUnits(-2_500L)
                    .withRunningBalanceMinorUnits(27_500L),
            )))

        whenever(clientCallsListEntriesForPeriod(
            "some-service-token",
            someAccountUid(),
            "2026-05-01T00:00:00Z",
            "2026-06-01T00:00:00Z",
        ))

        then(theLastEntriesResponse()) {
            this.openingBalanceMinorUnits shouldBe 20_000L
            this.closingBalanceMinorUnits shouldBe 27_500L
            this.entries.size shouldBe 1
        }
        and(madeTheFollowingHttpRequest {
            url.startsWith("/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            url.contains("periodStart=") shouldBe true
            url.contains("periodEnd=") shouldBe true
        }
    }

    private fun clientCallsListEntries(serviceToken: String, accountId: UUID) =
        Action<ActionContext> {
            lastEntriesResponse = ledgerClient.listEntries(serviceToken, accountId)
        }

    private fun clientCallsListEntriesForPeriod(
        serviceToken: String,
        accountId: UUID,
        periodStart: String,
        periodEnd: String,
    ) = Action<ActionContext> {
        lastEntriesResponse = ledgerClient.listEntries(serviceToken, accountId, periodStart, periodEnd)
    }

    private fun theLastEntriesResponse() = StateCollector { _ ->
        lastEntriesResponse ?: error("No entries response captured")
    }

    private fun clientCallsRegisterLedgerAccount(serviceToken: String, request: RegisterLedgerAccountRequest) =
        Action<ActionContext> {
            lastRegisterResponse = ledgerClient.registerLedgerAccount(serviceToken, request)
        }

    private fun clientCallsPostTransaction(serviceToken: String, request: PostTransactionRequest) =
        Action<ActionContext> {
            lastPostResponse = ledgerClient.postTransaction(serviceToken, request)
        }

    private fun clientCallsPostTransactionCapturingError(serviceToken: String, request: PostTransactionRequest) =
        Action<ActionContext> {
            lastThrowable = runCatching { ledgerClient.postTransaction(serviceToken, request) }.exceptionOrNull()
        }

    private fun theLastRegisterResponse() = StateCollector { _ ->
        lastRegisterResponse ?: error("No register-account response captured")
    }

    private fun theLastPostResponse() = StateCollector { _ ->
        lastPostResponse ?: error("No post-transaction response captured")
    }

    private fun theLastThrowable() = StateCollector { _ ->
        lastThrowable ?: error("No throwable captured")
    }

    private fun clientCallsPlaceHold(serviceToken: String, request: PlaceHoldRequest) =
        Action<ActionContext> {
            lastHoldResponse = ledgerClient.placeHold(serviceToken, request)
        }

    private fun clientCallsPlaceHoldCapturingError(serviceToken: String, request: PlaceHoldRequest) =
        Action<ActionContext> {
            lastThrowable = runCatching { ledgerClient.placeHold(serviceToken, request) }.exceptionOrNull()
        }

    private fun clientCallsSettleHold(serviceToken: String, holdId: UUID, posting: PostTransactionRequest) =
        Action<ActionContext> {
            lastSettleResponse = ledgerClient.settleHold(serviceToken, holdId, posting)
        }

    private fun clientCallsSettleHoldForAmount(
        serviceToken: String,
        holdId: UUID,
        posting: PostTransactionRequest,
        settleAmountMinorUnits: Long,
    ) = Action<ActionContext> {
        lastSettleResponse = ledgerClient.settleHold(serviceToken, holdId, posting, settleAmountMinorUnits)
    }

    private fun clientCallsReleaseHold(serviceToken: String, holdId: UUID, request: ReleaseHoldRequest) =
        Action<ActionContext> {
            lastHoldResponse = ledgerClient.releaseHold(serviceToken, holdId, request)
        }

    private fun clientCallsGetHold(serviceToken: String, holdId: UUID) =
        Action<ActionContext> {
            lastHoldResponse = ledgerClient.getHold(serviceToken, holdId)
        }

    private fun clientCallsGetBalances(serviceToken: String, accountId: UUID) =
        Action<ActionContext> {
            lastBalancesResponse = ledgerClient.getBalances(serviceToken, accountId)
        }

    private fun theLastHoldResponse() = StateCollector { _ ->
        lastHoldResponse ?: error("No hold response captured")
    }

    private fun theLastSettleResponse() = StateCollector { _ ->
        lastSettleResponse ?: error("No settle-hold response captured")
    }

    private fun theLastBalancesResponse() = StateCollector { _ ->
        lastBalancesResponse ?: error("No balances response captured")
    }

    @Test
    fun listTransactionEntriesReturnsBothLegsOfTheTransaction() {
        given(listTransactionEntriesReturns(someTransferUid(), aTransactionEntriesResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-05-03T12:00:00Z")
            .withType("PAYMENT")
            .withDescription("rent")
            .withEntries(
                aTransactionEntry()
                    .withEntryId(someEntryId)
                    .withAccountId(someAccountUid())
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(-2_500L)
                    .withRunningBalanceMinorUnits(7_500L)
                    .withDescription("rent")
                    .withTransactionType("PAYMENT"),
                aTransactionEntry()
                    .withEntryId(anotherEntryId)
                    .withAccountId(anotherAccountUid())
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(2_500L)
                    .withRunningBalanceMinorUnits(-7_500L)
                    .withDescription("rent")
                    .withTransactionType("PAYMENT"),
            )))

        whenever(clientCallsListTransactionEntries("some-service-token", someTransferUid()))

        then(theLastTransactionEntriesResponse()) {
            this.transactionId shouldBe someTransferUid()
            this.postedAt shouldBe "2026-05-03T12:00:00Z"
            this.type shouldBe "PAYMENT"
            this.description shouldBe "rent"
            this.entries shouldBe listOf(
                LedgerEntryProjection(
                    entryId = someEntryId,
                    transactionId = someTransferUid(),
                    postedAt = "2026-05-03T12:00:00Z",
                    amountMinorUnits = -2_500L,
                    runningBalanceMinorUnits = 7_500L,
                    description = "rent",
                    transactionType = "PAYMENT",
                    accountId = someAccountUid(),
                ),
                LedgerEntryProjection(
                    entryId = anotherEntryId,
                    transactionId = someTransferUid(),
                    postedAt = "2026-05-03T12:00:00Z",
                    amountMinorUnits = 2_500L,
                    runningBalanceMinorUnits = -7_500L,
                    description = "rent",
                    transactionType = "PAYMENT",
                    accountId = anotherAccountUid(),
                ),
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/transactions/${someTransferUid()}/entries"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
        }
    }

    @Test
    fun anUnknownTransactionSurfacesAsAClientResponseException() {
        given(listTransactionEntriesReturnsNotFound(someTransferUid()))

        whenever(clientAttemptsToListTransactionEntries("some-service-token", someTransferUid()))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    private fun clientCallsListTransactionEntries(serviceToken: String, transactionId: UUID) =
        Action<ActionContext> {
            lastTransactionEntriesResponse = ledgerClient.listTransactionEntries(serviceToken, transactionId)
        }

    private fun clientAttemptsToListTransactionEntries(serviceToken: String, transactionId: UUID) =
        Action<ActionContext> {
            lastThrowable = runCatching {
                ledgerClient.listTransactionEntries(serviceToken, transactionId)
            }.exceptionOrNull()
        }

    private fun theLastTransactionEntriesResponse() = StateCollector { _ ->
        lastTransactionEntriesResponse ?: error("No transaction entries response captured")
    }

    @Test
    fun listTransactionsForATraceReturnsEveryTransactionThatTraceCaused() {
        given(listTransactionsForTraceReturns("some-trace-id", aTracedTransactionsResponse()
            .withTraceId("some-trace-id")
            .withTransactions(
                aTransactionEntriesResponse()
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withType("PAYMENT_SETTLEMENT")
                    .withDescription("rent")
                    .withEntries(
                        aTransactionEntry()
                            .withEntryId(someEntryId)
                            .withAccountId(someAccountUid())
                            .withTransactionId(someTransferUid())
                            .withPostedAt("2026-05-03T12:00:00Z")
                            .withAmountMinorUnits(-2_500L)
                            .withRunningBalanceMinorUnits(7_500L)
                            .withDescription("rent")
                            .withTransactionType("PAYMENT_SETTLEMENT"),
                    ),
            )))

        whenever(clientCallsListTransactionsForTrace("some-service-token", "some-trace-id"))

        then(theLastTracedTransactionsResponse()) {
            this.traceId shouldBe "some-trace-id"
            this.transactions shouldBe listOf(
                TransactionEntriesResponse(
                    transactionId = someTransferUid(),
                    postedAt = "2026-05-03T12:00:00Z",
                    type = "PAYMENT_SETTLEMENT",
                    description = "rent",
                    entries = listOf(
                        LedgerEntryProjection(
                            entryId = someEntryId,
                            transactionId = someTransferUid(),
                            postedAt = "2026-05-03T12:00:00Z",
                            amountMinorUnits = -2_500L,
                            runningBalanceMinorUnits = 7_500L,
                            description = "rent",
                            transactionType = "PAYMENT_SETTLEMENT",
                            accountId = someAccountUid(),
                        ),
                    ),
                ),
            )
        }
        and(madeTheFollowingHttpRequest {
            url.startsWith("/ledger-service/api/v1/internal/ledger/transactions?traceId=")
        }) {
            method shouldBe GET
            url shouldBe "/ledger-service/api/v1/internal/ledger/transactions?traceId=some-trace-id"
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
        }
    }

    private fun clientCallsListTransactionsForTrace(serviceToken: String, traceId: String) =
        Action<ActionContext> {
            lastTracedTransactionsResponse = ledgerClient.listTransactionsForTrace(serviceToken, traceId)
        }

    private fun theLastTracedTransactionsResponse() = StateCollector { _ ->
        lastTracedTransactionsResponse ?: error("No traced transactions response captured")
    }
}
