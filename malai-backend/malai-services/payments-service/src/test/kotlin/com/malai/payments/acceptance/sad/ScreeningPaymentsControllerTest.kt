package com.malai.payments.acceptance.sad

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentState
import com.malai.payments.screening.ScreeningMode
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import java.util.UUID

class ScreeningPaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun whenTheModelDecisionCannotBeRecordedThePaymentFailsAndIsNeverDispatched() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000006aa"),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionFailsWithServerError())
        val friendsAccountNumber = "87654341"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-mdr-down-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(2_500L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe INTERNAL_SERVER_ERROR
        }

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 0
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun inEnforceModeTheModelDecisionWriteStillFailsThePaymentClosed() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000006bb"),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionFailsWithServerError())
        val friendsAccountNumber = "87654342"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-enforce-mdr-down-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(1_000_000L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe INTERNAL_SERVER_ERROR
        }

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 0
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun inEnforceModeADeciderTimeoutFailsOpenAndThePaymentProceeds() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000006cc"),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse().withOutcome("TIMED_OUT")))
        val friendsAccountNumber = "87654343"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-enforce-timeout-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(314_159L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "state" To "PENDING"
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.PENDING
            screeningDecisionId shouldBe null
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 1
        }
    }
}
