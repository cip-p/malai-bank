package com.malai.copilot.acceptance.happy

import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder.Companion.aBalancesResponse
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.TracedTransactionsResponseBuilder.Companion.aTracedTransactionsResponse
import com.malai.client.malai.ledger.builders.response.TransactionEntriesResponseBuilder.Companion.aTransactionEntriesResponse
import com.malai.client.malai.ledger.builders.response.TransactionEntriesResponseBuilder.TransactionEntryBuilder.Companion.aTransactionEntry
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.copilot.acceptance.CopilotAcceptanceTest
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import java.time.Instant
import java.util.UUID

private const val MDR_DECISIONS_URL = "/mdr-service/api/v1/internal/mdr/decisions"

class CopilotExplanationsControllerTest : CopilotAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val somePaymentId = UUID.fromString("00000000-0000-4000-8000-0000000000b1")
    private val initiatedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000c1")
    private val settledEventId = UUID.fromString("00000000-0000-4000-8000-0000000000c2")
    private val someDecisionId = UUID.fromString("00000000-0000-4000-8000-0000000000f1")
    private val someEntryId = UUID.fromString("00000000-0000-4000-8000-0000000000f2")
    private val debitEntryId = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
    private val creditEntryId = UUID.fromString("00000000-0000-4000-8000-0000000000a2")
    private val someTransactionId = UUID.fromString("00000000-0000-4000-8000-0000000000a3")
    private val someDebtorAccountId = UUID.fromString("00000000-0000-4000-8000-0000000000a4")
    private val someBeneficiaryAccountId = UUID.fromString("00000000-0000-4000-8000-0000000000a5")
    private val anotherDecisionId = UUID.fromString("00000000-0000-4000-8000-0000000000f3")
    private val anotherEntryId = UUID.fromString("00000000-0000-4000-8000-0000000000f4")
    private val firstRecordedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000c9")
    private val secondRecordedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000c3")
    private val someTraceId = "some-trace-id"
    private val theFabricatedLedgerEntryId = "00000000-0000-4000-8000-00000000dead"
    private val theArmedUngroundedAmountMinorUnits = 161_803L
    private val theArmedTimeoutAmountMinorUnits = 271_828L
    private val theArmedErrorAmountMinorUnits = 141_421L

    @Test
    fun explainingASettledFpsPaymentReturnsClaimsCitingThePaymentItsAuditEventsAndItsLedgerEntries() =
        malaiAcceptanceTest {
            given(theAuditChainIsAtGenesis())
            and(oauth2ServiceTokenEndpointReturnsToken())
            and(thePaymentRowIsAvailable())
            and(theLedgerLegsOfThatTraceAreAvailable())
            and(theDebtorsBalancesAreAvailable())
            and(theHoldForThatPaymentWasSettled())
            and(theNextGeneratedUuidWillBe(initiatedEventId, settledEventId, someDecisionId, someEntryId))
            and(anAuditEventIsRecorded(AuditEvent(
                actorId = someAdminUserId,
                actorType = ActorType.CUSTOMER,
                action = "PAYMENT_INITIATED",
                resourceType = "Payment",
                resourceId = somePaymentId.toString(),
                afterState = mapOf("state" to "PENDING", "rail" to "FPS"),
                ts = Instant.parse("2026-05-03T12:00:00Z"),
            )))
            and(anAuditEventIsRecorded(AuditEvent(
                actorId = someAdminUserId,
                actorType = ActorType.SYSTEM,
                action = "PAYMENT_SETTLED",
                resourceType = "Payment",
                resourceId = somePaymentId.toString(),
                afterState = mapOf("state" to "SETTLED", "rail" to "FPS"),
                ts = Instant.parse("2026-05-03T12:00:05Z"),
            )))
            and(recordModelDecisionReturns(aRecordModelDecisionResponse()
                .withEntryId(someEntryId)
                .withDecisionId(someDecisionId)
                .withSeq(1L)
                .withOutcome("COMPLETED")))

            whenever(copilotServiceReceives(
                post()
                    .url("/api/v1/admin/copilot/explanations")
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "some-idempotency-key")
                    .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
            ))

            then(theLastResponseHas()) {
                statusCode shouldBe CREATED
                body containsProp json {
                    "decisionId" To someDecisionId.toString()
                    "outcome" To "COMPLETED"
                    "claims" To listOf(
                        json {
                            "text" To something
                            "sources" To listOf(json {
                                "type" To "PAYMENT"
                                "id" To somePaymentId.toString()
                                noExtraProps()
                            })
                            noExtraProps()
                        },
                        json {
                            "text" To something
                            "sources" To listOf(json {
                                "type" To "HOLD"
                                "id" To somePaymentId.toString()
                                noExtraProps()
                            })
                            noExtraProps()
                        },
                        json {
                            "text" To something
                            "sources" To listOf(json {
                                "type" To "AUDIT_EVENT"
                                "id" To initiatedEventId.toString()
                                noExtraProps()
                            })
                            noExtraProps()
                        },
                        json {
                            "text" To something
                            "sources" To listOf(json {
                                "type" To "LEDGER_ENTRY"
                                "id" To debitEntryId.toString()
                                noExtraProps()
                            })
                            noExtraProps()
                        },
                        json {
                            "text" To something
                            "sources" To listOf(json {
                                "type" To "LEDGER_ENTRY"
                                "id" To creditEntryId.toString()
                                noExtraProps()
                            })
                            noExtraProps()
                        },
                        json {
                            "text" To something
                            "sources" To listOf(json {
                                "type" To "AUDIT_EVENT"
                                "id" To settledEventId.toString()
                                noExtraProps()
                            })
                            noExtraProps()
                        },
                    )
                    noExtraProps()
                }
            }
        }

    @Test
    fun exactlyOneAdvisoryModelDecisionIsAppendedWithOneRetrievedContextRefPerSourceRecord() =
        malaiAcceptanceTest {
            given(theAuditChainIsAtGenesis())
            and(oauth2ServiceTokenEndpointReturnsToken())
            and(thePaymentRowIsAvailable())
            and(theLedgerLegsOfThatTraceAreAvailable())
            and(theDebtorsBalancesAreAvailable())
            and(theHoldForThatPaymentWasSettled())
            and(theNextGeneratedUuidWillBe(settledEventId, someDecisionId, someEntryId))
            and(anAuditEventIsRecorded(AuditEvent(
                actorId = someAdminUserId,
                actorType = ActorType.SYSTEM,
                action = "PAYMENT_SETTLED",
                resourceType = "Payment",
                resourceId = somePaymentId.toString(),
                afterState = mapOf("state" to "SETTLED", "rail" to "FPS"),
                ts = Instant.parse("2026-05-03T12:00:05Z"),
            )))
            and(recordModelDecisionReturns(aRecordModelDecisionResponse()
                .withEntryId(someEntryId)
                .withDecisionId(someDecisionId)
                .withSeq(1L)
                .withOutcome("COMPLETED")))

            whenever(copilotServiceReceives(
                post()
                    .url("/api/v1/admin/copilot/explanations")
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "another-idempotency-key")
                    .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
            ))

            then(theLastResponseHas()) {
                statusCode shouldBe CREATED
            }

            and(copilotServiceMadeTheFollowingHttpRequest {
                url == "/mdr-service/api/v1/internal/mdr/decisions"
            }) {
                body containsProp json {
                    "entryId" To someEntryId.toString()
                    "decisionId" To someDecisionId.toString()
                    "correlationType" To "PAYMENT"
                    "correlationId" To somePaymentId.toString()
                    "advisory" To true
                    "modelId" To "malai-copilot-template"
                    "modelVersion" To "v1"
                    "promptTemplateId" To "copilot-payment"
                    "promptTemplateVersion" To "v2"
                    "promptTemplateHash" To something
                    "renderedPrompt" To something
                    "retrievedContext" To listOf(
                        json {
                            "type" To "PAYMENT"
                            "id" To somePaymentId.toString()
                            "contentHash" To something
                            noExtraProps()
                        },
                        json {
                            "type" To "HOLD"
                            "id" To somePaymentId.toString()
                            "contentHash" To something
                            noExtraProps()
                        },
                        json {
                            "type" To "LEDGER_ENTRY"
                            "id" To debitEntryId.toString()
                            "contentHash" To something
                            noExtraProps()
                        },
                        json {
                            "type" To "LEDGER_ENTRY"
                            "id" To creditEntryId.toString()
                            "contentHash" To something
                            noExtraProps()
                        },
                        json {
                            "type" To "AUDIT_EVENT"
                            "id" To settledEventId.toString()
                            "contentHash" To something
                            noExtraProps()
                        },
                    )
                    "outcome" To "COMPLETED"
                    "rawOutput" To something
                    "verdict" To something
                    "latencyMs" To something
                    "costMicros" To 0
                    "costCurrency" To "GBP"
                    noExtraProps()
                }
            }
        }

    @Test
    fun theRenderedPromptMasksAccountNumbersAndIbansAndCarriesBalanceAndHoldFigures() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePaymentRowIsAvailable())
        and(theLedgerLegsOfThatTraceAreAvailable())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasSettled())
        and(theNextGeneratedUuidWillBe(someDecisionId, someEntryId))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "a-third-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(copilotServiceMadeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions"
        }) {
            body shouldContain "****5678"
            body shouldNotContain "12345678"
            body shouldContain "****9012"
            body shouldNotContain "GB33BUKB20201555559012"
            body shouldContain "ledgerBalanceMinorUnits=250000"
            body shouldContain "heldAmountMinorUnits=15000"
            body shouldContain "availableBalanceMinorUnits=235000"
        }
    }

    @Test
    fun everySourceCitedByAClaimAppearsInThatEntrysRetrievedContextRefs() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePaymentRowIsAvailable())
        and(theLedgerLegsOfThatTraceAreAvailable())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasSettled())
        and(theNextGeneratedUuidWillBe(settledEventId, someDecisionId, someEntryId))
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someAdminUserId,
            actorType = ActorType.SYSTEM,
            action = "PAYMENT_SETTLED",
            resourceType = "Payment",
            resourceId = somePaymentId.toString(),
            afterState = mapOf("state" to "SETTLED", "rail" to "FPS"),
            ts = Instant.parse("2026-05-03T12:00:05Z"),
        )))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "grounding-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        val citedSources = mutableSetOf<String>()

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            citedSources += testingObjectMapper.readTree(body).at("/claims").flatMap { claim ->
                claim.at("/sources").values().map { "${it["type"].asString()}:${it["id"].asString()}" }
            }
            citedSources.shouldNotBeEmpty()
        }

        and(copilotServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            val recordedContextRefs = testingObjectMapper.readTree(body).at("/retrievedContext")
                .values().map { "${it["type"].asString()}:${it["id"].asString()}" }
                .toSet()
            recordedContextRefs shouldContainAll citedSources
        }
    }

    @Test
    fun aReplayedIdempotencyKeyReturnsTheFirstExplanationWithoutAppendingASecondEntry() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePaymentRowIsAvailable())
        and(theLedgerLegsOfThatTraceAreAvailable())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasSettled())
        and(theNextGeneratedUuidWillBe(someDecisionId, someEntryId, anotherDecisionId, anotherEntryId))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "replayed-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))
        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "replayed-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "outcome" To "COMPLETED"
                "claims" To something
                noExtraProps()
            }
        }

        and(copilotServiceMadeTheFollowingHttpRequests { url == MDR_DECISIONS_URL }) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun auditEventsSharingAMomentAreNarratedInChainOrderRatherThanEventIdOrder() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePaymentRowIsAvailable())
        and(theLedgerLegsOfThatTraceAreAvailable())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasSettled())
        and(theNextGeneratedUuidWillBe(
            firstRecordedEventId, secondRecordedEventId, someDecisionId, someEntryId))
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someAdminUserId,
            actorType = ActorType.SYSTEM,
            action = "PAYMENT_REFERRED",
            resourceType = "Payment",
            resourceId = somePaymentId.toString(),
            afterState = mapOf("state" to "REFERRED"),
            ts = Instant.parse("2026-05-03T12:00:04Z"),
        )))
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someAdminUserId,
            actorType = ActorType.ADMIN,
            action = "PAYMENT_RELEASED",
            resourceType = "Payment",
            resourceId = somePaymentId.toString(),
            afterState = mapOf("state" to "RELEASED"),
            ts = Instant.parse("2026-05-03T12:00:04Z"),
        )))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "chain-order-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            testingObjectMapper.readTree(body).at("/claims")
                .flatMap { claim -> claim.at("/sources") }
                .filter { it["type"].asString() == "AUDIT_EVENT" }
                .map { it["id"].asString() } shouldBe listOf(
                    firstRecordedEventId.toString(),
                    secondRecordedEventId.toString(),
                )
        }
    }

    @Test
    fun theArmedUngroundedSubjectRecordsItsRawOutputVerbatimAndServesNoClaims() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePaymentRowIsAvailable(theArmedUngroundedAmountMinorUnits))
        and(theLedgerLegsOfThatTraceAreAvailable())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasSettled())
        and(theNextGeneratedUuidWillBe(someDecisionId, someEntryId))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "ungrounded-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "outcome" To "COMPLETED"
                "claims" To emptyList<Any>()
                "refusalCode" To "GROUNDING_FAILED"
                noExtraProps()
            }
        }

        and(copilotServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            body shouldContain theFabricatedLedgerEntryId
            testingObjectMapper.readTree(body).run {
                at("/outcome").asString() shouldBe "COMPLETED"
                at("/rawOutput").asString() shouldContain theFabricatedLedgerEntryId
                has("verdict") shouldBe false
                has("errorDetail") shouldBe false
            }
        }
    }

    @Test
    fun theArmedTimeoutSubjectAppendsATimedOutEntryCarryingNoRawOutput() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePaymentRowIsAvailable(theArmedTimeoutAmountMinorUnits))
        and(theLedgerLegsOfThatTraceAreAvailable())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasSettled())
        and(theNextGeneratedUuidWillBe(someDecisionId, someEntryId))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("TIMED_OUT")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "timeout-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "outcome" To "TIMED_OUT"
                "claims" To emptyList<Any>()
                noExtraProps()
            }
        }

        and(copilotServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            testingObjectMapper.readTree(body).run {
                at("/outcome").asString() shouldBe "TIMED_OUT"
                has("rawOutput") shouldBe false
                has("verdict") shouldBe false
                has("errorDetail") shouldBe false
            }
        }
    }

    @Test
    fun aDeciderFailureAppendsAnErroredEntryCarryingTheFailureDetail() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePaymentRowIsAvailable(theArmedErrorAmountMinorUnits))
        and(theLedgerLegsOfThatTraceAreAvailable())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasSettled())
        and(theNextGeneratedUuidWillBe(someDecisionId, someEntryId))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("ERRORED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "errored-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "outcome" To "ERRORED"
                "claims" To emptyList<Any>()
                noExtraProps()
            }
        }

        and(copilotServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            testingObjectMapper.readTree(body).run {
                at("/outcome").asString() shouldBe "ERRORED"
                at("/errorDetail").asString().shouldNotBeEmpty()
                has("rawOutput") shouldBe false
                has("verdict") shouldBe false
            }
        }
    }

    private fun thePaymentRowIsAvailable(amountMinorUnits: Long = 2_500L) =
        getPaymentReturns(somePaymentId, anInternalPaymentResponse()
            .withPaymentId(somePaymentId)
            .withState("SETTLED")
            .withRail("FPS")
            .withDirection("OUTBOUND")
            .withAmountMinorUnits(amountMinorUnits)
            .withCurrency("GBP")
            .withCreatedAt("2026-05-03T12:00:00Z")
            .withTraceId(someTraceId)
            .withDebtorAccountId(someDebtorAccountId)
            .withDebtorSortCode("65-82-37")
            .withDebtorAccountNo("12345678")
            .withBeneficiaryName("some-beneficiary")
            .withBeneficiaryIban("GB33BUKB20201555559012"))

    private fun theLedgerLegsOfThatTraceAreAvailable() =
        listTransactionsForTraceReturns(someTraceId, aTracedTransactionsResponse()
            .withTraceId(someTraceId)
            .withTransactions(
                aTransactionEntriesResponse()
                    .withTransactionId(someTransactionId)
                    .withPostedAt("2026-05-03T12:00:02Z")
                    .withType("PAYMENT_SETTLEMENT")
                    .withDescription("rent")
                    .withEntries(
                        aTransactionEntry()
                            .withEntryId(debitEntryId)
                            .withAccountId(someDebtorAccountId)
                            .withTransactionId(someTransactionId)
                            .withPostedAt("2026-05-03T12:00:02Z")
                            .withAmountMinorUnits(-2_500L)
                            .withRunningBalanceMinorUnits(250_000L)
                            .withDescription("rent"),
                        aTransactionEntry()
                            .withEntryId(creditEntryId)
                            .withAccountId(someBeneficiaryAccountId)
                            .withTransactionId(someTransactionId)
                            .withPostedAt("2026-05-03T12:00:02Z")
                            .withAmountMinorUnits(2_500L)
                            .withRunningBalanceMinorUnits(-2_500L)
                            .withDescription("rent"),
                    ),
            ))

    private fun theHoldForThatPaymentWasSettled() =
        getHoldReturns(somePaymentId, aHoldResponse()
            .withHoldId(somePaymentId)
            .withAccountId(someDebtorAccountId)
            .withAmountMinorUnits(2_500L)
            .withStatus("SETTLED")
            .withPlacedAt("2026-05-03T12:00:00Z")
            .withResolvedAt("2026-05-03T12:00:02Z"))

    private fun theDebtorsBalancesAreAvailable() =
        getBalancesReturns(aBalancesResponse()
            .withAccountId(someDebtorAccountId)
            .withLedgerBalanceMinorUnits(250_000L)
            .withHeldAmountMinorUnits(15_000L)
            .withAvailableBalanceMinorUnits(235_000L))
}
