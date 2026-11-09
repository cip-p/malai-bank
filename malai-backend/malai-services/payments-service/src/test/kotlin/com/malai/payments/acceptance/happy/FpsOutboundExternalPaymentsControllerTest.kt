package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.SettleHoldResponseBuilder.Companion.aSettleHoldResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentState
import com.malai.payments.messages.ReturnReason
import com.malai.payments.support.WithPayments.Companion.SCHEME_SETTLEMENT_ACCOUNT_ID
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import java.util.UUID

class FpsOutboundExternalPaymentsControllerTest : PaymentsAcceptanceTest() {

    private val unknownExternalAccountNumber = "99990001"

    @Test
    fun fpsToExternalDestinationLandsPendingWithNullBeneficiaryAccountId() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001100"),
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(unknownExternalAccountNumber))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse()
            .withHoldId(somePaymentUid())
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(2_500L)))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "fps-ext-pending-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(unknownExternalAccountNumber)
                    .withAmountMinorUnits(2_500L)
                    .withToName("Alice External")
                    .withDescription("salary to friend"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "PENDING"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000001100"
                "fromAccountId" To someAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To unknownExternalAccountNumber
                "toName" To "Alice External"
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "salary to friend"
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.PENDING
            beneficiaryAccountId.shouldBeNull()
            beneficiarySortCode shouldBe malaiSortCode
            beneficiaryAccountNo shouldBe unknownExternalAccountNumber
            beneficiaryName shouldBe "Alice External"
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
            this[0].beneficiarySortCode shouldBe malaiSortCode
            this[0].beneficiaryAccountNumber shouldBe unknownExternalAccountNumber
            this[0].beneficiaryName shouldBe "Alice External"
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
        }
    }

    @Test
    fun settlementOfExternalPaymentSettlesTheHoldWithoutCreditingALocalBeneficiary() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001101"),
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(unknownExternalAccountNumber))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(somePaymentUid())
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "fps-ext-settle-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(unknownExternalAccountNumber)
                    .withAmountMinorUnits(1_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        and(theStubScheduleSettlesPayment(
            paymentId = somePaymentUid(),
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000001101"),
        ))

        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/payments/${somePaymentUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode.value() shouldBe 200
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "SETTLED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000001101"
                "fromAccountId" To someAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To unknownExternalAccountNumber
                "amountMinorUnits" To 1_500
                "currency" To "GBP"
                noExtraProps()
            }
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/${somePaymentUid()}/settle"
        }) {
            method shouldBe POST
            body containsProp json {
                "transactionId" To something
                "type" To "PAYMENT_SETTLEMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -1_500
                        "counterpartyId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        noExtraProps()
                    },
                    json {
                        "accountId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "amountMinorUnits" To 1_500
                        "counterpartyId" To someAccountUid().toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe listOf("PAYMENT_INITIATED", "PAYMENT_SETTLED")
        }
    }

    @Test
    fun duplicateExternalSettledMessageIsDeduplicated() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001102"),
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(unknownExternalAccountNumber))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(somePaymentUid())
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "fps-ext-settle-dedup-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(unknownExternalAccountNumber)
                    .withAmountMinorUnits(1_000L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        val endToEndId = UUID.fromString("00000000-0000-4000-9000-000000001102")
        and(theStubScheduleSettlesPayment(somePaymentUid(), endToEndId))
        and(theStubScheduleSettlesPayment(somePaymentUid(), endToEndId))

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.SETTLED
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.count { it["action"] == "PAYMENT_SETTLED" } shouldBe 1
        }
    }

    @Test
    fun returnedConsumerTransitionsExternalPaymentToReturnedAndReleasesTheHold() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001200"),
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(unknownExternalAccountNumber))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(releaseHoldReturns(aHoldResponse()
            .withHoldId(somePaymentUid())
            .withAccountId(someAccountUid())
            .withStatus("RELEASED")
            .withResolvedAt("2026-10-15T10:30:00Z")))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "fps-ext-return-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(unknownExternalAccountNumber)
                    .withAmountMinorUnits(3_000L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        and(theStubScheduleReturnsPayment(
            paymentId = somePaymentUid(),
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000001200"),
            reason = ReturnReason.INVALID_ACCOUNT,
        ))

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.RETURNED
            returnReason shouldBe ReturnReason.INVALID_ACCOUNT
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/${somePaymentUid()}/release"
        }) {
            method shouldBe POST
            body containsProp json {
                "resolvedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe listOf("PAYMENT_INITIATED", "PAYMENT_RETURNED")
        }
    }

    @Test
    fun duplicateReturnedMessageIsDeduplicatedNoSecondRelease() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001300"),
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(unknownExternalAccountNumber))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(releaseHoldReturns(aHoldResponse()
            .withHoldId(somePaymentUid())
            .withAccountId(someAccountUid())
            .withStatus("RELEASED")
            .withResolvedAt("2026-10-15T10:30:00Z")))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "fps-ext-return-dedup-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(unknownExternalAccountNumber)
                    .withAmountMinorUnits(1_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        val endToEndId = UUID.fromString("00000000-0000-4000-9000-000000001300")
        and(theStubScheduleReturnsPayment(somePaymentUid(), endToEndId, reason = ReturnReason.INVALID_ACCOUNT))
        and(theStubScheduleReturnsPayment(somePaymentUid(), endToEndId, reason = ReturnReason.INVALID_ACCOUNT))

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.RETURNED
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.count { it["action"] == "PAYMENT_RETURNED" } shouldBe 1
        }
    }
}
