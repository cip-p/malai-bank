package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.PostedEntryBuilder.Companion.aPostedEntry
import com.malai.client.malai.payments.builders.request.InternalInitiatePaymentRequestBuilder.Companion.anInternalInitiatePaymentRequest
import com.malai.commons.model.Currency
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import dev.kensa.Action
import dev.kensa.GivensContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class InternalPaymentsControllerTest : PaymentsAcceptanceTest() {

    private val scheduledEndToEndId: UUID = UUID.fromString("00000000-0000-4000-9000-0000000e2e01")
    private val screeningDecisionId: UUID = UUID.fromString("00000000-0000-4000-9000-00000000d1d1")
    private val settledEarlyPaymentId: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ae01")
    private val settledLatePaymentId: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ae02")

    @Test
    fun scheduledFpsInitiationPlacesAHoldAndLandsPendingWithTheCallerSuppliedEndToEndId() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePaymentUid()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse()
            .withHoldId(somePaymentUid())
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(2_500L)
            .withStatus("PLACED")))
        val payeeAccountNumber = "87651234"
        and(anotherCustomersAccountExists(payeeAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/internal/payments")
                .header("Content-Type", "application/json")
                .body(anInternalInitiatePaymentRequest()
                    .withEndToEndId(scheduledEndToEndId)
                    .withFromAccountId(someAccountUid())
                    .withAccountHolderId(someAccountHolderUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(payeeAccountNumber)
                    .withToName("Rent Collector")
                    .withAmountMinorUnits(2_500L)
                    .withReference("rent"))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "PENDING"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To scheduledEndToEndId.toString()
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To something
                "toSortCode" To malaiSortCode
                "toAccountNumber" To payeeAccountNumber
                "toName" To "Rent Collector"
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "rent"
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            rail.name shouldBe "FPS"
            direction shouldBe PaymentDirection.OUTBOUND
            state shouldBe PaymentState.PENDING
            debtorAccountId shouldBe someAccountUid()
            beneficiarySortCode shouldBe malaiSortCode
            beneficiaryAccountNo shouldBe payeeAccountNumber
            beneficiaryName shouldBe "Rent Collector"
            amountMinorUnits shouldBe 2_500L
            currency.name shouldBe "GBP"
            description shouldBe "rent"
            endToEndId shouldBe scheduledEndToEndId
            createdByAccountHolderId shouldBe someAccountHolderUid()
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
            body containsProp json {
                "holdId" To somePaymentUid().toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 2_500
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 1
            this[0].paymentId shouldBe somePaymentUid()
            this[0].endToEndId shouldBe scheduledEndToEndId
            this[0].amountMinorUnits shouldBe 2_500L
            this[0].beneficiaryAccountNumber shouldBe payeeAccountNumber
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
            this[0]["actor_type"] shouldBe "SYSTEM"
        }
    }

    @Test
    fun scheduledOnUsInitiationRoutesAsABookTransferAndSettlesSynchronously() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePaymentUid(), someTransferUid()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")
            .withEntries(
                aPostedEntry().withEntryId(randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-1_500L),
                aPostedEntry().withEntryId(randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(1_500L),
            )))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/internal/payments")
                .header("Content-Type", "application/json")
                .body(anInternalInitiatePaymentRequest()
                    .withEndToEndId(scheduledEndToEndId)
                    .withFromAccountId(someAccountUid())
                    .withAccountHolderId(someAccountHolderUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(1_500L)
                    .withReference("savings sweep"))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "SETTLED"
                "rail" To "BOOK_TRANSFER"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To scheduledEndToEndId.toString()
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To anotherAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To toAccountNumber()
                "amountMinorUnits" To 1_500
                "currency" To "GBP"
                "description" To "savings sweep"
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            rail.name shouldBe "BOOK_TRANSFER"
            direction shouldBe PaymentDirection.OUTBOUND
            state shouldBe PaymentState.SETTLED
            debtorAccountId shouldBe someAccountUid()
            beneficiaryAccountId shouldBe anotherAccountUid()
            amountMinorUnits shouldBe 1_500L
            endToEndId shouldBe scheduledEndToEndId
            createdByAccountHolderId shouldBe someAccountHolderUid()
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            method shouldBe POST
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "type" To "PAYMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "description" To "savings sweep"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -1_500
                        "counterpartyId" To anotherAccountUid().toString()
                        "description" To "savings sweep"
                        noExtraProps()
                    },
                    json {
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 1_500
                        "counterpartyId" To someAccountUid().toString()
                        "description" To "savings sweep"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
            this[0]["actor_type"] shouldBe "SYSTEM"
        }
    }

    @Test
    fun reInitiationWithAnAlreadyUsedEndToEndIdReturnsTheExistingPaymentWithoutASecondPaymentOrCommand() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePaymentUid()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse()
            .withHoldId(somePaymentUid())
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(2_500L)
            .withStatus("PLACED")))
        val payeeAccountNumber = "87651235"
        and(anotherCustomersAccountExists(payeeAccountNumber))

        val request = anInternalInitiatePaymentRequest()
            .withEndToEndId(scheduledEndToEndId)
            .withFromAccountId(someAccountUid())
            .withAccountHolderId(someAccountHolderUid())
            .withToSortCode(malaiSortCode)
            .withToAccountNumber(payeeAccountNumber)
            .withToName("Rent Collector")
            .withAmountMinorUnits(2_500L)
            .withReference("rent")

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/internal/payments")
                .header("Content-Type", "application/json")
                .body(request)
                .withServiceJwtToken()
        ))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/internal/payments")
                .header("Content-Type", "application/json")
                .body(request)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "PENDING"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To scheduledEndToEndId.toString()
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To something
                "toSortCode" To malaiSortCode
                "toAccountNumber" To payeeAccountNumber
                "toName" To "Rent Collector"
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "rent"
                noExtraProps()
            }
        }

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun theInternalReadReturnsThePaymentsOwnFacts() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aSettledFpsPaymentExists())

        whenever(paymentsServiceReceives(
            get().url("/api/v1/internal/payments/${somePaymentUid()}").withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "SETTLED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "rent"
                "paymentPurpose" To "RENT"
                "endToEndId" To scheduledEndToEndId.toString()
                "screeningDecisionId" To screeningDecisionId.toString()
                "createdAt" To "2026-10-15T10:30:00Z"
                "createdByAccountHolderId" To someAccountHolderUid().toString()
                "copVerdictOverride" To false
                "debtorAccountId" To someAccountUid().toString()
                "debtorSortCode" To "040004"
                "debtorAccountNo" To "12345678"
                "debtorName" To "Sarah Chen"
                "beneficiaryAccountId" To anotherAccountUid().toString()
                "beneficiarySortCode" To "040004"
                "beneficiaryAccountNo" To "87651236"
                "beneficiaryName" To "Rent Collector"
                "traceId" To "some-trace-id"
                noExtraProps()
            }
        }
    }

    @Test
    fun theSettledWindowReadReturnsTheFactsTheTypologiesConsume() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aSettledFpsPaymentExists())

        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/settled")
                .queryParam("from", "2026-10-15T00:00:00Z")
                .queryParam("until", "2026-10-16T00:00:00Z")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "payments" To listOf(
                    json {
                        "paymentId" To somePaymentUid().toString()
                        "state" To "SETTLED"
                        "rail" To "FPS"
                        "direction" To "OUTBOUND"
                        "amountMinorUnits" To 2_500
                        "currency" To "GBP"
                        "createdAt" To "2026-10-15T10:30:00Z"
                        "endToEndId" To scheduledEndToEndId.toString()
                        "debtorAccountId" To someAccountUid().toString()
                        "debtorSortCode" To "040004"
                        "debtorAccountNo" To "12345678"
                        "debtorName" To "Sarah Chen"
                        "beneficiaryAccountId" To anotherAccountUid().toString()
                        "beneficiarySortCode" To "040004"
                        "beneficiaryAccountNo" To "87651236"
                        "beneficiaryName" To "Rent Collector"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun theSettledWindowReadReturnsOnlySettledPaymentsInsideTheHalfOpenWindowOldestFirst() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentInState(settledLatePaymentId, PaymentState.SETTLED, "2026-10-15T18:00:00Z"))
        and(aPaymentInState(settledEarlyPaymentId, PaymentState.SETTLED, "2026-10-15T00:00:00Z"))
        and(aPaymentInState(randomUUID(), PaymentState.SETTLED, "2026-10-14T23:59:59Z"))
        and(aPaymentInState(randomUUID(), PaymentState.SETTLED, "2026-10-16T00:00:00Z"))
        and(aPaymentInState(randomUUID(), PaymentState.PENDING, "2026-10-15T12:00:00Z"))
        and(aPaymentInState(randomUUID(), PaymentState.REFERRED, "2026-10-15T12:00:00Z"))
        and(aPaymentInState(randomUUID(), PaymentState.DECLINED, "2026-10-15T12:00:00Z"))
        and(aPaymentInState(randomUUID(), PaymentState.RETURNED, "2026-10-15T12:00:00Z"))

        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/settled")
                .queryParam("from", "2026-10-15T00:00:00Z")
                .queryParam("until", "2026-10-16T00:00:00Z")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "payments" To listOf(
                    json {
                        "paymentId" To settledEarlyPaymentId.toString()
                        "state" To "SETTLED"
                        "rail" To "FPS"
                        "direction" To "OUTBOUND"
                        "amountMinorUnits" To 1_000
                        "currency" To "GBP"
                        "createdAt" To "2026-10-15T00:00:00Z"
                        "endToEndId" To something
                        noExtraProps()
                    },
                    json {
                        "paymentId" To settledLatePaymentId.toString()
                        "state" To "SETTLED"
                        "rail" To "FPS"
                        "direction" To "OUTBOUND"
                        "amountMinorUnits" To 1_000
                        "currency" To "GBP"
                        "createdAt" To "2026-10-15T18:00:00Z"
                        "endToEndId" To something
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    private fun aPaymentInState(
        paymentId: UUID,
        state: PaymentState,
        createdAt: String,
    ) = Action<GivensContext> {
        paymentRepository().save(PaymentEntity(
            paymentId = paymentId,
            rail = Rail.FPS,
            direction = PaymentDirection.OUTBOUND,
            state = state,
            amountMinorUnits = 1_000L,
            currency = Currency.GBP,
            endToEndId = randomUUID(),
            createdAt = Instant.parse(createdAt),
        ))
    }

    private fun aSettledFpsPaymentExists() = Action<GivensContext> {
        paymentRepository().save(PaymentEntity(
            paymentId = somePaymentUid(),
            rail = Rail.FPS,
            direction = PaymentDirection.OUTBOUND,
            state = PaymentState.SETTLED,
            debtorAccountId = someAccountUid(),
            debtorSortCode = "040004",
            debtorAccountNo = "12345678",
            debtorName = "Sarah Chen",
            beneficiaryAccountId = anotherAccountUid(),
            beneficiarySortCode = "040004",
            beneficiaryAccountNo = "87651236",
            beneficiaryName = "Rent Collector",
            paymentPurpose = "RENT",
            amountMinorUnits = 2_500L,
            currency = Currency.GBP,
            description = "rent",
            endToEndId = scheduledEndToEndId,
            screeningDecisionId = screeningDecisionId,
            createdAt = Instant.parse("2026-10-15T10:30:00Z"),
            createdByAccountHolderId = someAccountHolderUid(),
            traceId = "some-trace-id",
        ))
    }
}
