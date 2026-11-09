package com.malai.payments.acceptance.sad

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.mdr.builders.response.ActionableOutputResponseBuilder.Companion.anActionableOutputResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentState
import com.malai.payments.scheduler.ReferredPaymentResolver
import com.malai.payments.screening.ScreeningMode
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import dev.kensa.Action
import dev.kensa.ActionContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

private val SOME_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000009c1")
private val SOME_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000009e1")
private val SOME_RULING_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000009e2")
private val SOME_END_TO_END_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000009f1")
private val SOME_INITIATED_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000009a1")
private val SOME_REFERRAL_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000009a2")
private val SOME_ENACTMENT_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000009a3")

private const val DECLINE_OUTPUT =
    """{"decision":"DECLINE","riskScore":80,"reasons":["AMOUNT_AT_OR_ABOVE_THRESHOLD"]}"""
private const val NOT_JSON_AT_ALL = "the analyst typed prose into the output box"
private const val A_VERDICT_THE_RESOLVER_DOES_NOT_KNOW =
    """{"decision":"ESCALATE_TO_MLRO","riskScore":80,"reasons":["NEEDS_A_HUMAN"]}"""

class ReferredPaymentResolverTest : PaymentsAcceptanceTest() {

    private fun theReferredPaymentResolverSweeps() = Action<ActionContext> {
        getBean(ReferredPaymentResolver::class.java).resolveReferredPayments()
    }

    @Test
    fun aRefusedGateLeavesThePaymentParkedWithItsHoldIntact() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            SOME_END_TO_END_ID,
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_INITIATED_AUDIT_EVENT_ID,
            SOME_REFERRAL_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        and(actionableOutputIsRefused(SOME_DECISION_ID))
        val friendsAccountNumber = "87654371"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-refused-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(1_000_000L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.REFERRED
            screeningDecisionId shouldBe SOME_DECISION_ID
        }

        and(paymentsServiceMadeTheFollowingHttpRequests {
            url == "/ledger-service/api/v1/internal/ledger/holds/${somePaymentUid()}/release"
        }) {
            this shouldHaveSize 0
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe listOf("PAYMENT_INITIATED", "PAYMENT_REFERRED")
        }
    }

    @Test
    fun actionableOutputTheResolverCannotParseLeavesThePaymentParked() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            SOME_END_TO_END_ID,
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_INITIATED_AUDIT_EVENT_ID,
            SOME_REFERRAL_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        and(actionableOutputReturns(SOME_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_DECISION_ID)
            .withRuling("EDIT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput(NOT_JSON_AT_ALL)))
        val friendsAccountNumber = "87654372"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-unparseable-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(1_000_000L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.REFERRED
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe listOf("PAYMENT_INITIATED", "PAYMENT_REFERRED")
        }
    }

    @Test
    fun aVerdictTheResolverDoesNotUnderstandLeavesThePaymentParked() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            SOME_END_TO_END_ID,
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_INITIATED_AUDIT_EVENT_ID,
            SOME_REFERRAL_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        and(actionableOutputReturns(SOME_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_DECISION_ID)
            .withRuling("EDIT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput(A_VERDICT_THE_RESOLVER_DOES_NOT_KNOW)))
        val friendsAccountNumber = "87654373"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-unknown-verdict-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(1_000_000L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.REFERRED
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun aGateFailureLeavesThePaymentParkedForTheNextSweep() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            SOME_END_TO_END_ID,
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_INITIATED_AUDIT_EVENT_ID,
            SOME_REFERRAL_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        and(actionableOutputFailsWithServerError(SOME_DECISION_ID))
        val friendsAccountNumber = "87654374"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-gate-down-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(1_000_000L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.REFERRED
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun aLaterDispositionReGovernsAPaymentThatAnEarlierSweepLeftParked() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            SOME_END_TO_END_ID,
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_INITIATED_AUDIT_EVENT_ID,
            SOME_REFERRAL_AUDIT_EVENT_ID,
            SOME_ENACTMENT_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(releaseHoldReturns(aHoldResponse()
            .withHoldId(somePaymentUid())
            .withAccountId(someAccountUid())
            .withStatus("RELEASED")))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        and(actionableOutputIsRefused(SOME_DECISION_ID))
        val friendsAccountNumber = "87654375"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-re-rule-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(1_000_000L)
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.REFERRED
        }

        and(actionableOutputReturns(SOME_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_DECISION_ID)
            .withRuling("ACCEPT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput(DECLINE_OUTPUT)))

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.DECLINED
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe
                listOf("PAYMENT_INITIATED", "PAYMENT_REFERRED", "PAYMENT_DECLINED")
        }
    }
}
