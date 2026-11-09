package com.malai.aml.acceptance.happy

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.support.PersistedAmlCase
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.Companion.aDecisionRecordResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.ContextRefBuilder.Companion.aPaymentContextRef
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

private const val MDR_DECISIONS_URL = "/mdr-service/api/v1/internal/mdr/decisions"

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005c1")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005a0")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade5")
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-00000000005a")

private val SOME_SAR_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005d1")
private val SOME_SAR_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005d2")
private val ANOTHER_SAR_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005d3")
private val ANOTHER_SAR_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005d4")

class AdminAmlSarDraftTest : AmlAcceptanceTest() {

    @Test
    fun theDraftIsRecordedAsANonAdvisoryModelDecisionCitingTheWholeCaseTimeline() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_SAR_DECISION_ID, SOME_SAR_ENTRY_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_SAR_ENTRY_ID)
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(amlServiceReceives(theDraftRequest("a-sar-draft-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To SOME_SAR_DECISION_ID.toString()
                "outcome" To "COMPLETED"
                "narrative" To something
                noExtraProps()
            }
            body shouldContain "Malai Bank reports activity it considers suspicious on account $SOME_ACCOUNT_ID"
        }

        and(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            body containsProp json {
                "entryId" To SOME_SAR_ENTRY_ID.toString()
                "decisionId" To SOME_SAR_DECISION_ID.toString()
                "correlationType" To "CASE"
                "correlationId" To SOME_CASE_ID.toString()
                "advisory" To false
                "modelId" To "malai-sar-template"
                "modelVersion" To "1"
                "promptTemplateId" To "sar-narrative"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To listOf(
                    json {
                        "type" To "PAYMENT"
                        "id" To SOME_PAYMENT_ID.toString()
                        "contentHash" To "a1".repeat(32)
                        noExtraProps()
                    },
                    json {
                        "type" To "ALERT_DECISION"
                        "id" To SOME_ALERT_ID.toString()
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
    fun everyRecordTheNarrativeCitesAppearsInThatEntrysRetrievedContextRefs() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_SAR_DECISION_ID, SOME_SAR_ENTRY_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_SAR_ENTRY_ID)
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(amlServiceReceives(theDraftRequest("a-cited-refs-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            val recorded = testingObjectMapper.readTree(body)
            val citedRecords = recorded.at("/verdict/statements")
                .flatMap { statement -> statement.at("/sources") }
                .map { "${it["type"].asString()}:${it["id"].asString()}" }
                .toSet()
            val contextRefs = recorded.at("/retrievedContext")
                .values().map { "${it["type"].asString()}:${it["id"].asString()}" }
                .toSet()

            contextRefs shouldContainAll citedRecords
        }
    }

    @Test
    fun requestingADraftOnAnOpenCaseIsTheOfficerActionThatMovesItToInvestigating() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_SAR_DECISION_ID, SOME_SAR_ENTRY_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(theAlertRecordIsReadable(rulingInForce = null))
        and(theEvidencePaymentIsReadable())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_SAR_ENTRY_ID)
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(amlServiceReceives(theDraftRequest("an-open-case-draft-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "INVESTIGATING",
                alertDecisionIds = listOf(SOME_ALERT_ID),
                sarDecisionId = SOME_SAR_DECISION_ID,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-10-15T12:00:00Z"),
                closedAt = null,
            )
        }
    }

    @Test
    fun theCaseDetailShowsTheSarStateOnceADraftHasBeenRequested() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_SAR_DECISION_ID, SOME_SAR_ENTRY_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_SAR_ENTRY_ID)
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withSeq(1L)
            .withOutcome("COMPLETED")))
        and(decisionRecordReturns(SOME_SAR_DECISION_ID, aDecisionRecordResponse()
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withCorrelationType("CASE")
            .withCorrelationId(SOME_CASE_ID.toString())
            .withModelId("malai-sar-template")))

        whenever(amlServiceReceives(theDraftRequest("a-detail-after-draft-key")))
        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "status" To "INVESTIGATING"
                "sar" To json {
                    "decisionId" To SOME_SAR_DECISION_ID.toString()
                    noExtraProps()
                }
            }
        }
    }

    @Test
    fun aRetriedDraftRequestAppendsExactlyOneEntry() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(
            SOME_SAR_DECISION_ID, SOME_SAR_ENTRY_ID, ANOTHER_SAR_DECISION_ID, ANOTHER_SAR_ENTRY_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_SAR_ENTRY_ID)
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(amlServiceReceives(theDraftRequest("a-retried-sar-draft-key")))
        whenever(amlServiceReceives(theDraftRequest("a-retried-sar-draft-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To SOME_SAR_DECISION_ID.toString()
                "outcome" To "COMPLETED"
                "narrative" To something
                noExtraProps()
            }
        }

        and(amlServiceMadeTheFollowingHttpRequests { url == MDR_DECISIONS_URL }) {
            this shouldHaveSize 1
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            sarDecisionId shouldBe SOME_SAR_DECISION_ID
        }
    }

    private fun theDraftRequest(idempotencyKey: String) = post()
        .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/sar-draft")
        .header("Idempotency-Key", idempotencyKey)
        .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin"))

    private fun theAlertRecordIsReadable(rulingInForce: String? = "ACCEPT") =
        decisionRecordReturns(SOME_ALERT_ID, anAlertRecord().withRulingInForce(rulingInForce))

    private fun anAlertRecord(): DecisionRecordResponseBuilder = aDecisionRecordResponse()
        .withDecisionId(SOME_ALERT_ID)
        .withCorrelationType("ACCOUNT")
        .withCorrelationId(SOME_ACCOUNT_ID.toString())
        .withTs("2026-10-15T10:05:00Z")
        .withVerdict(mapOf("typology" to "STRUCTURING", "riskScore" to 70))
        .withRetrievedContext(aPaymentContextRef(SOME_PAYMENT_ID, "a1".repeat(32)))

    private fun theEvidencePaymentIsReadable() = getPaymentReturns(SOME_PAYMENT_ID, anInternalPaymentResponse()
        .withPaymentId(SOME_PAYMENT_ID)
        .withCreatedAt("2026-10-15T10:01:00Z")
        .withAmountMinorUnits(199_900L)
        .withBeneficiaryName("Acme Holdings")
        .withBeneficiarySortCode("65-82-37")
        .withBeneficiaryAccountNo("12345678"))
}
