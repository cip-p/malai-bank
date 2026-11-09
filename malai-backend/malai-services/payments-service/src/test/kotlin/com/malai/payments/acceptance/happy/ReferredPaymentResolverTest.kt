package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.SettleHoldResponseBuilder.Companion.aSettleHoldResponse
import com.malai.client.malai.mdr.builders.response.ActionableOutputResponseBuilder.Companion.anActionableOutputResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.commons.model.Currency
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.scheduler.ReferredPaymentResolver
import com.malai.payments.screening.ScreeningMode
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import java.time.Instant
import java.util.UUID

private val SOME_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000008c1")
private val SOME_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000008e1")
private val SOME_RULING_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000008e2")
private val SOME_END_TO_END_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000008f1")
private val SOME_INITIATED_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000008a1")
private val SOME_REFERRAL_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000008a2")
private val SOME_ENACTMENT_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000008a3")

private const val WATCHLIST_COUNTERPARTY = "Carol Singh"
private const val DECLINE_OUTPUT =
    """{"decision":"DECLINE","riskScore":80,"reasons":["AMOUNT_AT_OR_ABOVE_THRESHOLD"]}"""
private const val APPROVE_OUTPUT =
    """{"decision":"APPROVE","riskScore":80,"reasons":["REVIEWED_BY_OPS"]}"""

class ReferredPaymentResolverTest : PaymentsAcceptanceTest() {

    private fun theReferredPaymentResolverSweeps() = Action<ActionContext> {
        getBean(ReferredPaymentResolver::class.java).resolveReferredPayments()
    }

    @Test
    fun acceptingTheDeclineVerdictDeclinesThePaymentAndReleasesItsHold() = malaiAcceptanceTest {
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
        and(actionableOutputReturns(SOME_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_DECISION_ID)
            .withRuling("ACCEPT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput(DECLINE_OUTPUT)))
        val friendsAccountNumber = "87654361"
        val friendsAccountId = UUID.fromString("00000000-0000-4000-9000-000000000561")
        and(anotherCustomersAccountExists(friendsAccountNumber, friendsAccountId))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-decline-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(1_000_000L)
                    .withDescription("Deposit")
                    .withToName("Bob Harrison"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            this shouldBe PaymentEntity(
                paymentId = somePaymentUid(),
                rail = Rail.FPS,
                direction = PaymentDirection.OUTBOUND,
                state = PaymentState.DECLINED,
                returnReason = null,
                debtorAccountId = someAccountUid(),
                debtorSortCode = null,
                debtorAccountNo = null,
                debtorName = null,
                debtorIban = null,
                debtorBic = null,
                beneficiaryAccountId = friendsAccountId,
                beneficiarySortCode = malaiSortCode,
                beneficiaryAccountNo = friendsAccountNumber,
                beneficiaryName = "Bob Harrison",
                beneficiaryIban = null,
                beneficiaryBic = null,
                paymentPurpose = null,
                amountMinorUnits = 1_000_000L,
                currency = Currency.GBP,
                description = "Deposit",
                endToEndId = SOME_END_TO_END_ID,
                copVerdictOverride = false,
                screeningDecisionId = SOME_DECISION_ID,
                fxTargetCurrency = null,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                createdByAccountHolderId = someAccountHolderUid(),
                traceId = null,
            )
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions/$SOME_DECISION_ID/actionable-output"
        }) {
            headers["Authorization"]!! shouldStartWith "Bearer "
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

        and(paymentsServiceMadeTheFollowingHttpRequests {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            this shouldHaveSize 0
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe
                listOf("PAYMENT_INITIATED", "PAYMENT_REFERRED", "PAYMENT_DECLINED")
            this[2]["resource_id"] shouldBe somePaymentUid().toString()
            this[2]["actor_type"] shouldBe "SYSTEM"
            this[2]["after_state"].toString() shouldContain SOME_DECISION_ID.toString()
        }
    }

    @Test
    fun anEditToApproveReleasesThePaymentWhichDispatchesAndSettlesAsNormal() = malaiAcceptanceTest {
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
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(somePaymentUid())
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(UUID.randomUUID())
            .withPostedAt("2026-10-15T10:30:00Z")))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        and(actionableOutputReturns(SOME_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_DECISION_ID)
            .withRuling("EDIT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput(APPROVE_OUTPUT)))
        val friendsAccountNumber = "87654362"
        val friendsAccountId = UUID.fromString("00000000-0000-4000-9000-000000000562")
        and(anotherCustomersAccountExists(friendsAccountNumber, friendsAccountId))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-release-1")
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
            statusCode shouldBe CREATED
            body containsProp json {
                "state" To "REFERRED"
            }
        }

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.PENDING
            screeningDecisionId shouldBe SOME_DECISION_ID
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 1
            this[0].paymentId shouldBe somePaymentUid()
            this[0].endToEndId shouldBe SOME_END_TO_END_ID
            this[0].amountMinorUnits shouldBe 1_000_000L
            this[0].beneficiaryAccountNumber shouldBe friendsAccountNumber
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe
                listOf("PAYMENT_INITIATED", "PAYMENT_REFERRED", "PAYMENT_RELEASED")
            this[2]["resource_id"] shouldBe somePaymentUid().toString()
            this[2]["actor_type"] shouldBe "SYSTEM"
            this[2]["after_state"].toString() shouldContain SOME_DECISION_ID.toString()
        }

        and(theStubScheduleSettlesPayment(somePaymentUid(), SOME_END_TO_END_ID))

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.SETTLED
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe
                listOf("PAYMENT_INITIATED", "PAYMENT_REFERRED", "PAYMENT_RELEASED", "PAYMENT_SETTLED")
        }
    }

    @Test
    fun aReleasedSwiftPaymentReachesTheSchemeWithTheTargetCurrencyItWasInitiatedWith() = malaiAcceptanceTest {
        given(screeningModeIs(ScreeningMode.ENFORCE))
        and(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
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
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        and(actionableOutputReturns(SOME_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_DECISION_ID)
            .withRuling("EDIT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput(APPROVE_OUTPUT)))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-release-swift-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToIban("DE89370400440532013000")
                    .withToBic("COBADEFFXXX")
                    .withAmountMinorUnits(1_000_000L)
                    .withCurrency("EUR")
                    .withPaymentPurpose("Rent")
                    .withToName(WATCHLIST_COUNTERPARTY))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.REFERRED
            currency shouldBe Currency.GBP
            fxTargetCurrency shouldBe Currency.EUR
        }

        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.PENDING
        }

        and(theSubmittedSwiftOutbounds()) {
            this shouldHaveSize 1
            this[0].paymentId shouldBe somePaymentUid()
            this[0].currency shouldBe "GBP"
            this[0].amountMinorUnits shouldBe 1_000_000L
            this[0].targetCurrency shouldBe "EUR"
        }
    }

    @Test
    fun aSecondSweepOverAnAlreadyEnactedPaymentChangesNothing() = malaiAcceptanceTest {
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
        and(actionableOutputReturns(SOME_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_DECISION_ID)
            .withRuling("ACCEPT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput(DECLINE_OUTPUT)))
        val friendsAccountNumber = "87654363"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "resolver-idempotent-1")
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
        whenever(theReferredPaymentResolverSweeps())

        then(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.DECLINED
        }

        and(paymentsServiceMadeTheFollowingHttpRequests {
            url == "/ledger-service/api/v1/internal/ledger/holds/${somePaymentUid()}/release"
        }) {
            this shouldHaveSize 1
        }

        and(paymentsServiceMadeTheFollowingHttpRequests {
            url == "/mdr-service/api/v1/internal/mdr/decisions/$SOME_DECISION_ID/actionable-output"
        }) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe
                listOf("PAYMENT_INITIATED", "PAYMENT_REFERRED", "PAYMENT_DECLINED")
        }
    }
}
