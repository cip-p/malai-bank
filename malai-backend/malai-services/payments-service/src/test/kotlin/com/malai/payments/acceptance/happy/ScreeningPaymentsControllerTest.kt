package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.commons.model.Currency
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.screening.ScreeningMode
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

private val SOME_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005a1")
private val SOME_REFERRAL_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005a2")
private val SOME_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005c1")
private val SOME_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005e1")
private val SOME_END_TO_END_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005f1")
private const val MDR_DECISIONS_URL = "/mdr-service/api/v1/internal/mdr/decisions"
private const val WATCHLIST_VERSION = "2026-07-01"
private const val WATCHLIST_COUNTERPARTY = "Carol Singh"

class ScreeningPaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun anOrdinaryFpsPaymentRecordsACompletedModelDecisionBeforeItIsDispatched() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000005aa"),
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        val friendsAccountNumber = "87654331"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-shadow-1")
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
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "PENDING"
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.PENDING
        }

        and(paymentsServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            method shouldBe POST
            body containsProp json {
                "entryId" To SOME_ENTRY_ID.toString()
                "decisionId" To SOME_DECISION_ID.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To somePaymentUid().toString()
                "advisory" To false
                "modelId" To "malai-screening-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "payment-screening"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To listOf(
                    json {
                        "type" To "ACCOUNT"
                        "id" To someAccountUid().toString()
                        "contentHash" To something
                        noExtraProps()
                    },
                    json {
                        "type" To "WATCHLIST"
                        "id" To WATCHLIST_VERSION
                        "contentHash" To something
                        noExtraProps()
                    },
                )
                "outcome" To "COMPLETED"
                "rawOutput" To """{"decision":"APPROVE","riskScore":10,"reasons":[]}"""
                "verdict" To json {
                    "decision" To "APPROVE"
                    "riskScore" To 10
                    "reasons" To emptyList<String>()
                    noExtraProps()
                }
                "latencyMs" To something
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 1
            this[0].paymentId shouldBe somePaymentUid()
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
        }
    }

    @Test
    fun aSwiftPaymentIsScreenedTheSameWayAndProceeds() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000005bb"),
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-shadow-swift-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToIban("DE89370400440532013000")
                    .withToBic("COBADEFFXXX")
                    .withAmountMinorUnits(10_000L)
                    .withCurrency("EUR")
                    .withPaymentPurpose("Rent")
                    .withToName("Helga Mueller"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "state" To "PENDING"
                "rail" To "SWIFT"
            }
        }

        and(paymentsServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            body containsProp json {
                "correlationType" To "PAYMENT"
                "correlationId" To somePaymentUid().toString()
                "advisory" To false
                "modelId" To "malai-screening-rules"
                "outcome" To "COMPLETED"
                "verdict" To json {
                    "decision" To "APPROVE"
                    "riskScore" To 10
                    "reasons" To emptyList<String>()
                    noExtraProps()
                }
            }
        }

        and(theSubmittedSwiftOutbounds()) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
        }
    }

    @Test
    fun aBookTransferIsNotScreenedAtAll() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000005cc"),
            someTransferUid(),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-book-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "state" To "SETTLED"
                "rail" To "BOOK_TRANSFER"
            }
        }

        and(paymentsServiceMadeTheFollowingHttpRequests { url == MDR_DECISIONS_URL }) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
        }
    }

    @Test
    fun aPaymentAtTheThresholdRecordsADeclineVerdictAndStillProceedsInShadowMode() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000005dd"),
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        val friendsAccountNumber = "87654332"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-threshold-1")
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
                "state" To "PENDING"
            }
        }

        and(paymentsServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            body containsProp json {
                "outcome" To "COMPLETED"
                "rawOutput" To
                    """{"decision":"DECLINE","riskScore":80,"reasons":["AMOUNT_AT_OR_ABOVE_THRESHOLD"]}"""
                "verdict" To json {
                    "decision" To "DECLINE"
                    "riskScore" To 80
                    "reasons" To listOf("AMOUNT_AT_OR_ABOVE_THRESHOLD")
                    noExtraProps()
                }
            }
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
        }
    }

    @Test
    fun aPaymentToTheWatchlistCounterpartyRecordsADeclineVerdictAtAnyAmountAndStillProceeds() =
        malaiAcceptanceTest {
            given(theAuditChainIsAtGenesis())
            and(thereIsNoIdempotencyKeyInTheDatabase())
            and(thereAreNoPaymentsInTheDatabase())
            and(thereIsNoSqsMessageDedupInTheDatabase())
            and(theFpsSchemeIsReset())
            and(nowIs("2026-10-15T10:30:00Z"))
            and(theNextGeneratedUuidWillBe(
                somePaymentUid(),
                UUID.fromString("00000000-0000-4000-9000-0000000005ee"),
                SOME_DECISION_ID,
                SOME_ENTRY_ID,
                SOME_AUDIT_EVENT_ID,
            ))
            and(ownAccountsExistInLookup())
            and(oauth2ServiceTokenEndpointReturnsToken())
            and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
            and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
            val friendsAccountNumber = "87654333"
            and(anotherCustomersAccountExists(friendsAccountNumber))

            whenever(paymentsServiceReceives(
                post()
                    .url("/api/v1/payments")
                    .header("Idempotency-Key", "screening-watchlist-1")
                    .header("Content-Type", "application/json")
                    .body(anInitiatePaymentRequest()
                        .withFromAccountId(someAccountUid())
                        .withToSortCode(malaiSortCode)
                        .withToAccountNumber(friendsAccountNumber)
                        .withAmountMinorUnits(100L)
                        .withToName(WATCHLIST_COUNTERPARTY))
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            ))

            then(theLastResponseHas()) {
                statusCode shouldBe CREATED
                body containsProp json {
                    "state" To "PENDING"
                }
            }

            and(paymentsServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
                body containsProp json {
                    "outcome" To "COMPLETED"
                    "verdict" To json {
                        "decision" To "DECLINE"
                        "riskScore" To 25
                        "reasons" To listOf("WATCHLIST_COUNTERPARTY")
                        noExtraProps()
                    }
                }
            }

            and(theSubmittedFpsOutbounds()) {
                this shouldHaveSize 1
            }

            and(theAuditEventRowsInTheDatabase()) {
                this shouldHaveSize 1
                this[0]["action"] shouldBe "PAYMENT_INITIATED"
            }
        }

    @Test
    fun inEnforceModeAThresholdPaymentIsParkedAsReferredAndNeverDispatched() = malaiAcceptanceTest {
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
            SOME_AUDIT_EVENT_ID,
            SOME_REFERRAL_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_ENTRY_ID)
            .withDecisionId(SOME_DECISION_ID)))
        val friendsAccountNumber = "87654351"
        val friendsAccountId = UUID.fromString("00000000-0000-4000-9000-000000000551")
        and(anotherCustomersAccountExists(friendsAccountNumber, friendsAccountId))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-enforce-threshold-1")
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

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "REFERRED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To SOME_END_TO_END_ID.toString()
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To friendsAccountId.toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To friendsAccountNumber
                "toName" To "Bob Harrison"
                "amountMinorUnits" To 1_000_000
                "currency" To "GBP"
                "description" To "Deposit"
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            this shouldBe PaymentEntity(
                paymentId = somePaymentUid(),
                rail = Rail.FPS,
                direction = PaymentDirection.OUTBOUND,
                state = PaymentState.REFERRED,
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
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                createdByAccountHolderId = someAccountHolderUid(),
                traceId = null,
            )
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
            body containsProp json {
                "holdId" To somePaymentUid().toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 1_000_000
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
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
            this[1]["after_state"].toString() shouldContain SOME_DECISION_ID.toString()
            this[1]["resource_id"] shouldBe somePaymentUid().toString()
        }
    }

    @Test
    fun aReferredPaymentIsReadableWithItsNewStateAndCarriesNoDeclineReason() = malaiAcceptanceTest {
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
            SOME_AUDIT_EVENT_ID,
            SOME_REFERRAL_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        val friendsAccountNumber = "87654352"
        val friendsAccountId = UUID.fromString("00000000-0000-4000-9000-000000000552")
        and(anotherCustomersAccountExists(friendsAccountNumber, friendsAccountId))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-enforce-watchlist-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(friendsAccountNumber)
                    .withAmountMinorUnits(100L)
                    .withToName(WATCHLIST_COUNTERPARTY))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/payments/${somePaymentUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "REFERRED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To SOME_END_TO_END_ID.toString()
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To friendsAccountId.toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To friendsAccountNumber
                "toName" To WATCHLIST_COUNTERPARTY
                "amountMinorUnits" To 100
                "currency" To "GBP"
                noExtraProps()
            }
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun inEnforceModeAnUnflaggedPaymentProceedsAndDispatchesExactlyAsInShadow() = malaiAcceptanceTest {
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
            SOME_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        val friendsAccountNumber = "87654353"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-enforce-clean-1")
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
            this[0].paymentId shouldBe somePaymentUid()
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe listOf("PAYMENT_INITIATED")
        }
    }

    @Test
    fun theArmedAmountRecordsATimedOutDecisionAndThePaymentStillProceeds() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-0000000005ff"),
            SOME_DECISION_ID,
            SOME_ENTRY_ID,
            SOME_AUDIT_EVENT_ID,
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse().withOutcome("TIMED_OUT")))
        val friendsAccountNumber = "87654334"
        and(anotherCustomersAccountExists(friendsAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "screening-timeout-1")
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

        and(paymentsServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            body containsProp json {
                "entryId" To SOME_ENTRY_ID.toString()
                "decisionId" To SOME_DECISION_ID.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To somePaymentUid().toString()
                "advisory" To false
                "modelId" To "malai-screening-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "payment-screening"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To listOf(
                    json {
                        "type" To "ACCOUNT"
                        "id" To someAccountUid().toString()
                        "contentHash" To something
                        noExtraProps()
                    },
                    json {
                        "type" To "WATCHLIST"
                        "id" To WATCHLIST_VERSION
                        "contentHash" To something
                        noExtraProps()
                    },
                )
                "outcome" To "TIMED_OUT"
                "latencyMs" To something
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
        }
    }
}
