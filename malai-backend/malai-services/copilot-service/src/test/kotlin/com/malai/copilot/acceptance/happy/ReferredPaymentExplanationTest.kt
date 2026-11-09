package com.malai.copilot.acceptance.happy

import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder.Companion.aBalancesResponse
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.TracedTransactionsResponseBuilder.Companion.aTracedTransactionsResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.copilot.acceptance.CopilotAcceptanceTest
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import java.time.Instant
import java.util.UUID

private const val MDR_DECISIONS_URL = "/mdr-service/api/v1/internal/mdr/decisions"

class ReferredPaymentExplanationTest : CopilotAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val somePaymentId = UUID.fromString("00000000-0000-4000-8000-0000000000d1")
    private val someScreeningDecisionId = UUID.fromString("00000000-0000-4000-8000-0000000000d2")
    private val someDebtorAccountId = UUID.fromString("00000000-0000-4000-8000-0000000000d3")
    private val referralEventId = UUID.fromString("00000000-0000-4000-8000-0000000000e1")
    private val decisionRecordedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000e2")
    private val dispositionRecordedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000e3")
    private val releasedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000e4")
    private val declinedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000e5")
    private val initiatedEventId = UUID.fromString("00000000-0000-4000-8000-0000000000e6")
    private val someDecisionId = UUID.fromString("00000000-0000-4000-8000-0000000000f1")
    private val someEntryId = UUID.fromString("00000000-0000-4000-8000-0000000000f2")
    private val someTraceId = "some-referred-trace-id"

    @Test
    fun aReferredPaymentsExplanationCitesTheScreeningDecisionAndTheReferralEvent() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("REFERRED"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentIsStillPlaced())
        and(theNextGeneratedUuidWillBe(referralEventId, decisionRecordedEventId, someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(theScreeningDecisionWasRecorded())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "referred-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "outcome" To "COMPLETED"
                "claims" To listOf(
                    aClaimCiting("PAYMENT", somePaymentId.toString()),
                    aClaimCiting("HOLD", somePaymentId.toString()),
                    aClaimCiting("SCREENING_DECISION", someScreeningDecisionId.toString()),
                    aClaimCiting("AUDIT_EVENT", referralEventId.toString()),
                    aClaimCiting("AUDIT_EVENT", decisionRecordedEventId.toString()),
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun theScreeningDecisionAndTheHoldReachTheRecordedRetrievedContextRefs() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("REFERRED"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentIsStillPlaced())
        and(theNextGeneratedUuidWillBe(referralEventId, decisionRecordedEventId, someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(theScreeningDecisionWasRecorded())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "referred-context-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(copilotServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            testingObjectMapper.readTree(body).at("/retrievedContext")
                .values().map { "${it["type"].asString()}:${it["id"].asString()}" } shouldBe listOf(
                    "PAYMENT:$somePaymentId",
                    "HOLD:$somePaymentId",
                    "SCREENING_DECISION:$someScreeningDecisionId",
                    "AUDIT_EVENT:$referralEventId",
                    "AUDIT_EVENT:$decisionRecordedEventId",
                )
            body shouldContain "promptTemplateVersion\":\"v2"
        }
    }

    @Test
    fun theHoldOnAReferredPaymentIsCitableAsStillHoldingTheFunds() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("REFERRED"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentIsStillPlaced())
        and(theNextGeneratedUuidWillBe(referralEventId, decisionRecordedEventId, someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(theScreeningDecisionWasRecorded())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "referred-hold-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body.theTextOfTheClaimCiting("HOLD", somePaymentId.toString()) shouldContain "status=PLACED"
            body.theTextOfTheClaimCiting("HOLD", somePaymentId.toString()) shouldContain "resolvedAt=still held"
            body.theTextOfTheClaimCiting("HOLD", somePaymentId.toString()) shouldContain "amountMinorUnits=2500"
        }
    }

    @Test
    fun aReleasedPaymentsExplanationCitesTheDispositionAndTheReleaseEvent() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("PENDING"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentIsStillPlaced())
        and(theNextGeneratedUuidWillBe(
            referralEventId, decisionRecordedEventId, dispositionRecordedEventId, releasedEventId,
            someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(theScreeningDecisionWasRecorded())
        and(aHumanRuledOnTheScreeningDecision("APPROVED"))
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someAdminUserId,
            actorType = ActorType.SYSTEM,
            action = "PAYMENT_RELEASED",
            resourceType = "Payment",
            resourceId = somePaymentId.toString(),
            afterState = mapOf(
                "state" to "PENDING",
                "rail" to "FPS",
                "screeningDecisionId" to someScreeningDecisionId.toString(),
            ),
            ts = Instant.parse("2026-05-03T12:00:31Z"),
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
                .header("Idempotency-Key", "released-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body.theCitedSources() shouldBe listOf(
                "PAYMENT:$somePaymentId",
                "HOLD:$somePaymentId",
                "SCREENING_DECISION:$someScreeningDecisionId",
                "AUDIT_EVENT:$referralEventId",
                "AUDIT_EVENT:$decisionRecordedEventId",
                "AUDIT_EVENT:$dispositionRecordedEventId",
                "AUDIT_EVENT:$releasedEventId",
            )
            body.theTextOfTheClaimCiting("SCREENING_DECISION", someScreeningDecisionId.toString()) shouldContain
                "disposition=APPROVED"
        }
    }

    @Test
    fun aRulingAndTheReleaseItCausedShareAMomentAndAreNarratedInChainOrder() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("PENDING"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentIsStillPlaced())
        and(theNextGeneratedUuidWillBe(
            referralEventId, decisionRecordedEventId, dispositionRecordedEventId, releasedEventId,
            someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(theScreeningDecisionWasRecorded())
        and(aHumanRuledOnTheScreeningDecision("APPROVED", ts = theMomentOfTheRuling))
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someAdminUserId,
            actorType = ActorType.SYSTEM,
            action = "PAYMENT_RELEASED",
            resourceType = "Payment",
            resourceId = somePaymentId.toString(),
            afterState = mapOf(
                "state" to "PENDING",
                "rail" to "FPS",
                "screeningDecisionId" to someScreeningDecisionId.toString(),
            ),
            ts = theMomentOfTheRuling,
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
                .header("Idempotency-Key", "same-moment-ruling-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body.theCitedSources() shouldBe listOf(
                "PAYMENT:$somePaymentId",
                "HOLD:$somePaymentId",
                "SCREENING_DECISION:$someScreeningDecisionId",
                "AUDIT_EVENT:$referralEventId",
                "AUDIT_EVENT:$decisionRecordedEventId",
                "AUDIT_EVENT:$dispositionRecordedEventId",
                "AUDIT_EVENT:$releasedEventId",
            )
        }
    }

    @Test
    fun aDeclinedPaymentsExplanationCitesTheDeclineEventAndTheHoldGoingBack() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("DECLINED"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentWasReleased())
        and(theNextGeneratedUuidWillBe(
            referralEventId, decisionRecordedEventId, dispositionRecordedEventId, declinedEventId,
            someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(theScreeningDecisionWasRecorded())
        and(aHumanRuledOnTheScreeningDecision("REJECTED"))
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = someAdminUserId,
            actorType = ActorType.SYSTEM,
            action = "PAYMENT_DECLINED",
            resourceType = "Payment",
            resourceId = somePaymentId.toString(),
            afterState = mapOf(
                "state" to "DECLINED",
                "rail" to "FPS",
                "screeningDecisionId" to someScreeningDecisionId.toString(),
            ),
            ts = Instant.parse("2026-05-03T12:00:31Z"),
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
                .header("Idempotency-Key", "declined-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body.theCitedSources() shouldBe listOf(
                "PAYMENT:$somePaymentId",
                "HOLD:$somePaymentId",
                "SCREENING_DECISION:$someScreeningDecisionId",
                "AUDIT_EVENT:$referralEventId",
                "AUDIT_EVENT:$decisionRecordedEventId",
                "AUDIT_EVENT:$dispositionRecordedEventId",
                "AUDIT_EVENT:$declinedEventId",
            )
            body.theTextOfTheClaimCiting("HOLD", somePaymentId.toString()) shouldContain "status=RELEASED"
            body.theTextOfTheClaimCiting("HOLD", somePaymentId.toString()) shouldContain
                "resolvedAt=2026-05-03T12:00:31Z"
            body.theTextOfTheClaimCiting("SCREENING_DECISION", someScreeningDecisionId.toString()) shouldContain
                "disposition=REJECTED"
        }
    }

    @Test
    fun theScreeningStoryIsAssembledWithoutReadingTheMdrSoTheOnlyMdrCallIsTheCopilotsOwnAppend() =
        malaiAcceptanceTest {
            given(theAuditChainIsAtGenesis())
            and(oauth2ServiceTokenEndpointReturnsToken())
            and(theReferredPaymentRowIsAvailable("REFERRED"))
            and(itsTraceCausedNoPostings())
            and(theDebtorsBalancesAreAvailable())
            and(theHoldForThatPaymentIsStillPlaced())
            and(theNextGeneratedUuidWillBe(referralEventId, decisionRecordedEventId, someDecisionId, someEntryId))
            and(thePaymentWasReferredByScreening())
            and(theScreeningDecisionWasRecorded())
            and(recordModelDecisionReturns(aRecordModelDecisionResponse()
                .withEntryId(someEntryId)
                .withDecisionId(someDecisionId)
                .withSeq(1L)
                .withOutcome("COMPLETED")))

            whenever(copilotServiceReceives(
                post()
                    .url("/api/v1/admin/copilot/explanations")
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "no-mdr-read-idempotency-key")
                    .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
            ))

            then(theLastResponseHas()) {
                statusCode shouldBe CREATED
            }

            and(copilotServiceMadeTheFollowingHttpRequests { url.startsWith("/mdr-service") }) {
                this shouldHaveSize 1
                this.single().url shouldBe MDR_DECISIONS_URL
                this.single().method.name() shouldBe "POST"
            }
        }

    @Test
    fun aPaymentTheLedgerNeverHeldFundsForOmitsTheHoldRatherThanFailingTheExplanation() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("REFERRED"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theLedgerHasNoHoldForThatPayment())
        and(theNextGeneratedUuidWillBe(referralEventId, decisionRecordedEventId, someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(theScreeningDecisionWasRecorded())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "no-hold-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body.theCitedSources() shouldBe listOf(
                "PAYMENT:$somePaymentId",
                "SCREENING_DECISION:$someScreeningDecisionId",
                "AUDIT_EVENT:$referralEventId",
                "AUDIT_EVENT:$decisionRecordedEventId",
            )
        }
    }

    @Test
    fun aScreeningDecisionTheAuditPlaneSaysNothingAboutIsCitedAsNeitherRecordedNorRuled() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("REFERRED"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentIsStillPlaced())
        and(theNextGeneratedUuidWillBe(referralEventId, someDecisionId, someEntryId))
        and(thePaymentWasReferredByScreening())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "unrecorded-decision-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body.theCitedSources() shouldBe listOf(
                "PAYMENT:$somePaymentId",
                "HOLD:$somePaymentId",
                "SCREENING_DECISION:$someScreeningDecisionId",
                "AUDIT_EVENT:$referralEventId",
            )
            body.theTextOfTheClaimCiting("SCREENING_DECISION", someScreeningDecisionId.toString()) shouldContain
                "outcome=not recorded"
            body.theTextOfTheClaimCiting("SCREENING_DECISION", someScreeningDecisionId.toString()) shouldContain
                "disposition=not ruled"
        }
    }

    private fun aClaimCiting(type: String, id: String) = json {
        "text" To something
        "sources" To listOf(json {
            "type" To type
            "id" To id
            noExtraProps()
        })
        noExtraProps()
    }

    private fun String?.theCitedSources(): List<String> =
        testingObjectMapper.readTree(orEmpty()).at("/claims").flatMap { claim ->
            claim.at("/sources").values().map { "${it["type"].asString()}:${it["id"].asString()}" }
        }

    @Test
    fun anAccountNumberRecordedOnTheAuditPlaneNeverReachesTheRenderedPrompt() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theReferredPaymentRowIsAvailable("REFERRED"))
        and(itsTraceCausedNoPostings())
        and(theDebtorsBalancesAreAvailable())
        and(theHoldForThatPaymentIsStillPlaced())
        and(theNextGeneratedUuidWillBe(initiatedEventId, someDecisionId, someEntryId))
        and(thePaymentWasInitiatedAgainstAPublishedAccountNumber())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "redacted-audit-state-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body.theTextOfTheClaimCiting("AUDIT_EVENT", initiatedEventId.toString()) shouldContain
                "toAccountNumber=****3344"
            body.theTextOfTheClaimCiting("AUDIT_EVENT", initiatedEventId.toString()) shouldContain
                "toIban=******************5555"
            body.theTextOfTheClaimCiting("AUDIT_EVENT", initiatedEventId.toString()) shouldContain
                "toSortCode=30-00-03"
            body.orEmpty() shouldNotContain "11223344"
        }

        and(copilotServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            body.orEmpty() shouldNotContain "11223344"
            body.orEmpty() shouldNotContain "GB33BUKB20201555555555"
        }
    }

    private fun String?.theTextOfTheClaimCiting(type: String, id: String): String =
        testingObjectMapper.readTree(orEmpty()).at("/claims")
            .first { claim ->
                claim.at("/sources").any { it["type"].asString() == type && it["id"].asString() == id }
            }["text"].asString()

    private fun theReferredPaymentRowIsAvailable(state: String) =
        getPaymentReturns(somePaymentId, anInternalPaymentResponse()
            .withPaymentId(somePaymentId)
            .withState(state)
            .withRail("FPS")
            .withDirection("OUTBOUND")
            .withAmountMinorUnits(2_500L)
            .withCurrency("GBP")
            .withCreatedAt("2026-05-03T12:00:00Z")
            .withTraceId(someTraceId)
            .withDebtorAccountId(someDebtorAccountId)
            .withDebtorSortCode("65-82-37")
            .withDebtorAccountNo("12345678")
            .withBeneficiaryName("some-beneficiary")
            .withBeneficiaryIban("GB33BUKB20201555559012")
            .withScreeningDecisionId(someScreeningDecisionId))

    private fun itsTraceCausedNoPostings() =
        listTransactionsForTraceReturns(someTraceId, aTracedTransactionsResponse().withTraceId(someTraceId))

    private fun theHoldForThatPaymentIsStillPlaced() =
        getHoldReturns(somePaymentId, aHoldResponse()
            .withHoldId(somePaymentId)
            .withAccountId(someDebtorAccountId)
            .withAmountMinorUnits(2_500L)
            .withStatus("PLACED")
            .withPlacedAt("2026-05-03T12:00:00Z")
            .withResolvedAt(null))

    private fun theLedgerHasNoHoldForThatPayment() = getHoldReturnsNotFound(somePaymentId)

    private fun theHoldForThatPaymentWasReleased() =
        getHoldReturns(somePaymentId, aHoldResponse()
            .withHoldId(somePaymentId)
            .withAccountId(someDebtorAccountId)
            .withAmountMinorUnits(2_500L)
            .withStatus("RELEASED")
            .withPlacedAt("2026-05-03T12:00:00Z")
            .withResolvedAt("2026-05-03T12:00:31Z"))

    private fun theDebtorsBalancesAreAvailable() =
        getBalancesReturns(aBalancesResponse()
            .withAccountId(someDebtorAccountId)
            .withLedgerBalanceMinorUnits(250_000L)
            .withHeldAmountMinorUnits(2_500L)
            .withAvailableBalanceMinorUnits(247_500L))

    private fun thePaymentWasInitiatedAgainstAPublishedAccountNumber() = anAuditEventIsRecorded(AuditEvent(
        actorId = someAdminUserId,
        actorType = ActorType.CUSTOMER,
        action = "PAYMENT_INITIATED",
        resourceType = "Payment",
        resourceId = somePaymentId.toString(),
        afterState = mapOf(
            "state" to "REFERRED",
            "rail" to "FPS",
            "toSortCode" to "30-00-03",
            "toAccountNumber" to "11223344",
            "toIban" to "GB33BUKB20201555555555",
            "amountMinorUnits" to 2_500L,
        ),
        ts = Instant.parse("2026-05-03T12:00:01Z"),
    ))

    private fun thePaymentWasReferredByScreening() = anAuditEventIsRecorded(AuditEvent(
        actorId = someAdminUserId,
        actorType = ActorType.SYSTEM,
        action = "PAYMENT_REFERRED",
        resourceType = "Payment",
        resourceId = somePaymentId.toString(),
        afterState = mapOf(
            "state" to "REFERRED",
            "rail" to "FPS",
            "screeningDecisionId" to someScreeningDecisionId.toString(),
        ),
        ts = Instant.parse("2026-05-03T12:00:01Z"),
    ))

    private fun theScreeningDecisionWasRecorded() = anAuditEventIsRecorded(AuditEvent(
        actorId = null,
        actorType = ActorType.SYSTEM,
        action = "MDR_DECISION_RECORDED",
        resourceType = "ModelDecision",
        resourceId = someScreeningDecisionId.toString(),
        afterState = mapOf("entryId" to someEntryId.toString(), "outcome" to "COMPLETED"),
        ts = Instant.parse("2026-05-03T12:00:01Z"),
    ))

    private fun aHumanRuledOnTheScreeningDecision(
        disposition: String,
        ts: Instant = Instant.parse("2026-05-03T12:00:30Z"),
    ) = anAuditEventIsRecorded(AuditEvent(
        actorId = someAdminUserId,
        actorType = ActorType.ADMIN,
        action = "MDR_DISPOSITION_RECORDED",
        resourceType = "ModelDecision",
        resourceId = someScreeningDecisionId.toString(),
        afterState = mapOf("entryId" to someEntryId.toString(), "disposition" to disposition),
        ts = ts,
    ))

    private val theMomentOfTheRuling: Instant = Instant.parse("2026-05-03T12:00:30Z")
}
