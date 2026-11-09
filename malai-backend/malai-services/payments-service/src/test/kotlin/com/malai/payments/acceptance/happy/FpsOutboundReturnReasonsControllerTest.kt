package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentState
import com.malai.payments.messages.ReturnReason
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

class FpsOutboundReturnReasonsControllerTest : PaymentsAcceptanceTest() {

    private val externalAccountNumber = "99990050"

    @Test
    fun accountClosedReturnReasonIsRecordedOnPaymentAndExposedOnGetResponse() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000005100"),
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(externalAccountNumber))
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
                .header("Idempotency-Key", "fps-return-closed-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(externalAccountNumber)
                    .withAmountMinorUnits(4_000L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        and(theStubScheduleReturnsPayment(
            paymentId = somePaymentUid(),
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000005100"),
            reason = ReturnReason.ACCOUNT_CLOSED,
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
                "state" To "RETURNED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000005100"
                "fromAccountId" To someAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To externalAccountNumber
                "amountMinorUnits" To 4_000
                "currency" To "GBP"
                "returnReason" To "ACCOUNT_CLOSED"
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.RETURNED
            returnReason shouldBe ReturnReason.ACCOUNT_CLOSED
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
            val returned = this.first { it["action"] == "PAYMENT_RETURNED" }
            (returned["after_state"] as String) shouldContain "\"returnReason\": \"ACCOUNT_CLOSED\""
        }
    }

    @Test
    fun beneficiaryDeceasedReasonRoundTripsThroughTheLifecycle() = malaiAcceptanceTest {
        assertReasonRoundTrips(
            reason = ReturnReason.BENEFICIARY_DECEASED,
            idempotencyKey = "fps-return-deceased-1",
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000005200"),
        )
    }

    @Test
    fun recipientBankUnavailableReasonRoundTripsThroughTheLifecycle() = malaiAcceptanceTest {
        assertReasonRoundTrips(
            reason = ReturnReason.RECIPIENT_BANK_UNAVAILABLE,
            idempotencyKey = "fps-return-bank-down-1",
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000005300"),
        )
    }

    @Test
    fun accountNameMismatchReasonRoundTripsThroughTheLifecycle() = malaiAcceptanceTest {
        assertReasonRoundTrips(
            reason = ReturnReason.ACCOUNT_NAME_MISMATCH,
            idempotencyKey = "fps-return-mismatch-1",
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000005400"),
        )
    }

    @Test
    fun copVerdictOverrideTrueOnRequestPersistsOnEntityAndPropagatesToSubmitMessage() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000005500"),
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(externalAccountNumber))
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
                .header("Idempotency-Key", "fps-cop-override-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(externalAccountNumber)
                    .withAmountMinorUnits(2_500L)
                    .withCopVerdictOverride(true))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        and(thePaymentInTheDatabase(somePaymentUid())) {
            copVerdictOverride shouldBe true
            state shouldBe PaymentState.PENDING
        }

        and(theSubmittedFpsOutbounds()) {
            this.size shouldBe 1
            this[0].copVerdictOverride shouldBe true
        }

        and(theStubScheduleReturnsPayment(
            paymentId = somePaymentUid(),
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000005500"),
            reason = ReturnReason.ACCOUNT_NAME_MISMATCH,
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
                "state" To "RETURNED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000005500"
                "fromAccountId" To someAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To externalAccountNumber
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "returnReason" To "ACCOUNT_NAME_MISMATCH"
                noExtraProps()
            }
        }
    }

    private fun assertReasonRoundTrips(
        reason: ReturnReason,
        idempotencyKey: String,
        endToEndId: UUID,
    ) {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            endToEndId,
        ))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(externalAccountNumber))
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
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(externalAccountNumber)
                    .withAmountMinorUnits(1_000L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        and(theStubScheduleReturnsPayment(
            paymentId = somePaymentUid(),
            endToEndId = endToEndId,
            reason = reason,
        ))

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.RETURNED
            this.returnReason shouldBe reason
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/${somePaymentUid()}/release"
        }) {
            method shouldBe POST
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe listOf("PAYMENT_INITIATED", "PAYMENT_RETURNED")
        }
    }

}
