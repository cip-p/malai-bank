package com.malai.aml.acceptance.happy

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.support.PersistedAmlCase
import com.malai.client.malai.mdr.builders.response.ActionableOutputResponseBuilder
import com.malai.client.malai.mdr.builders.response.ActionableOutputResponseBuilder.Companion.anActionableOutputResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.Companion.aDecisionRecordResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.ContextRefBuilder.Companion.aPaymentContextRef
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val THE_DRAFTED_NARRATIVE =
    "Malai Bank reports activity it considers suspicious on account 00000000-0000-4000-9000-000000000ac7."
private const val THE_OFFICERS_OWN_NARRATIVE =
    "Malai Bank reports five sub-threshold payments to one beneficiary within twenty minutes."

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000f7")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ac7")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000adf7")
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000f7")
private val SOME_SAR_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005d7")
private val SOME_RULING_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ae17")
private val SOME_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa7")

class AdminAmlCaseCloseReportedTest : AmlAcceptanceTest() {

    @Test
    fun anAcceptRulingInForceFilesTheDraftedNarrativeAndClosesTheCaseAsReported() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aCaseAwaitingItsSarRuling())
        and(theGateServes(anAcceptedDraft()))
        and(theSarRecordIsReadable())
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(theCloseRequest("a-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "caseId" To SOME_CASE_ID.toString()
                "status" To "CLOSED_REPORTED"
                "updatedAt" To "2026-10-15T12:00:00Z"
                "sar" To json {
                    "decisionId" To SOME_SAR_DECISION_ID.toString()
                    "rulingInForce" To "ACCEPT"
                    "narrative" To THE_DRAFTED_NARRATIVE
                    noExtraProps()
                }
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "CLOSED_REPORTED",
                alertDecisionIds = listOf(SOME_ALERT_ID),
                sarDecisionId = SOME_SAR_DECISION_ID,
                sarNarrative = THE_DRAFTED_NARRATIVE,
                openedAt = Instant.parse("2026-10-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-10-15T12:00:00Z"),
                closedAt = Instant.parse("2026-10-15T12:00:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["seq"] shouldBe 1L
            this[0]["event_id"] shouldBe SOME_AUDIT_EVENT_ID
            (this[0]["ts"] as Timestamp).toInstant() shouldBe Instant.parse("2026-10-15T12:00:00Z")
            this[0]["actor_id"] shouldBe SOME_ADMIN_USER_ID
            this[0]["actor_type"] shouldBe "ADMIN"
            this[0]["action"] shouldBe "AML_CASE_CLOSED_REPORTED"
            this[0]["resource_type"] shouldBe "AmlCase"
            this[0]["resource_id"] shouldBe SOME_CASE_ID.toString()
            this[0]["before_state"] shouldBe """{"status": "INVESTIGATING"}"""
            this[0]["after_state"] shouldBe """{"ruling": "ACCEPT", "status": "CLOSED_REPORTED", """ +
                """"rulingEntryId": "$SOME_RULING_ENTRY_ID", "sarDecisionId": "$SOME_SAR_DECISION_ID"}"""
        }
    }

    @Test
    fun anEditRulingInForceFilesTheOfficersNarrativeRatherThanTheDrafters() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aCaseAwaitingItsSarRuling())
        and(theGateServes(anEditedDraft()))
        and(theSarRecordIsReadable(rulingInForce = "EDIT"))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(theCloseRequest("an-edited-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "status" To "CLOSED_REPORTED"
                "sar" To json {
                    "decisionId" To SOME_SAR_DECISION_ID.toString()
                    "rulingInForce" To "EDIT"
                    "narrative" To THE_OFFICERS_OWN_NARRATIVE
                    noExtraProps()
                }
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            status shouldBe "CLOSED_REPORTED"
            sarNarrative shouldBe THE_OFFICERS_OWN_NARRATIVE
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "AML_CASE_CLOSED_REPORTED"
            this[0]["after_state"] shouldBe """{"ruling": "EDIT", "status": "CLOSED_REPORTED", """ +
                """"rulingEntryId": "$SOME_RULING_ENTRY_ID", "sarDecisionId": "$SOME_SAR_DECISION_ID"}"""
        }
    }

    @Test
    fun aRetriedCloseEnactsExactlyOnce() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aCaseAwaitingItsSarRuling())
        and(theGateServes(anAcceptedDraft()))
        and(theSarRecordIsReadable())
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(theCloseRequest("a-retried-report-key")))
        whenever(amlServiceReceives(theCloseRequest("a-retried-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "caseId" To SOME_CASE_ID.toString()
                "status" To "CLOSED_REPORTED"
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "AML_CASE_CLOSED_REPORTED"
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            status shouldBe "CLOSED_REPORTED"
            sarNarrative shouldBe THE_DRAFTED_NARRATIVE
            closedAt shouldBe Instant.parse("2026-10-15T12:00:00Z")
        }
    }

    @Test
    fun theCaseDetailAfterCloseCarriesTheTerminalStatusAndTheFiledNarrative() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aCaseAwaitingItsSarRuling())
        and(theGateServes(anAcceptedDraft()))
        and(theSarRecordIsReadable())
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(theCloseRequest("a-detail-after-report-key")))
        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "caseId" To SOME_CASE_ID.toString()
                "accountId" To SOME_ACCOUNT_ID.toString()
                "status" To "CLOSED_REPORTED"
                "openedAt" To "2026-10-15T10:00:00Z"
                "updatedAt" To "2026-10-15T12:00:00Z"
                "alerts" To listOf(
                    json {
                        "decisionId" To SOME_ALERT_ID.toString()
                        "typology" To "STRUCTURING"
                        "riskScore" To 70
                        "raisedAt" To "2026-10-15T10:05:00Z"
                        "rulingInForce" To "ACCEPT"
                        "evidence" To listOf(
                            json {
                                "paymentId" To SOME_PAYMENT_ID.toString()
                                "contentHash" To "a1".repeat(32)
                                "rail" To "FPS"
                                "direction" To "OUTBOUND"
                                "amountMinorUnits" To 199_900
                                "currency" To "GBP"
                                "createdAt" To "2026-10-15T10:01:00Z"
                                "beneficiaryName" To "Acme Holdings"
                                "beneficiarySortCode" To "65-82-37"
                                "beneficiaryAccountNo" To "12345678"
                                noExtraProps()
                            },
                        )
                        noExtraProps()
                    },
                )
                "sar" To json {
                    "decisionId" To SOME_SAR_DECISION_ID.toString()
                    "rulingInForce" To "ACCEPT"
                    "narrative" To THE_DRAFTED_NARRATIVE
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    private fun theCloseRequest(idempotencyKey: String) = post()
        .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-reported")
        .header("Idempotency-Key", idempotencyKey)
        .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin"))

    private fun aCaseAwaitingItsSarRuling() = anAmlCaseExists(
        SOME_CASE_ID,
        SOME_ACCOUNT_ID,
        AmlCaseStatus.INVESTIGATING,
        listOf(SOME_ALERT_ID),
        "2026-10-15T10:00:00Z",
        sarDecisionId = SOME_SAR_DECISION_ID,
    )

    private fun theGateServes(output: ActionableOutputResponseBuilder) =
        actionableOutputReturns(SOME_SAR_DECISION_ID, output)

    private fun anAcceptedDraft() = anActionableOutputResponse()
        .withDecisionId(SOME_SAR_DECISION_ID)
        .withRuling("ACCEPT")
        .withRulingEntryId(SOME_RULING_ENTRY_ID)
        .withActionableOutput("""{"narrative":"$THE_DRAFTED_NARRATIVE","statements":[]}""")

    private fun anEditedDraft() = anActionableOutputResponse()
        .withDecisionId(SOME_SAR_DECISION_ID)
        .withRuling("EDIT")
        .withRulingEntryId(SOME_RULING_ENTRY_ID)
        .withActionableOutput("""{"narrative":"$THE_OFFICERS_OWN_NARRATIVE","statements":[]}""")

    private fun theSarRecordIsReadable(rulingInForce: String = "ACCEPT") =
        decisionRecordReturns(SOME_SAR_DECISION_ID, aDecisionRecordResponse()
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withCorrelationType("CASE")
            .withCorrelationId(SOME_CASE_ID.toString())
            .withModelId("malai-sar-template")
            .withRulingInForce(rulingInForce))

    private fun theAlertRecordIsReadable() = decisionRecordReturns(SOME_ALERT_ID, aDecisionRecordResponse()
        .withDecisionId(SOME_ALERT_ID)
        .withCorrelationType("ACCOUNT")
        .withCorrelationId(SOME_ACCOUNT_ID.toString())
        .withTs("2026-10-15T10:05:00Z")
        .withVerdict(mapOf("typology" to "STRUCTURING", "riskScore" to 70))
        .withRulingInForce("ACCEPT")
        .withRetrievedContext(aPaymentContextRef(SOME_PAYMENT_ID, "a1".repeat(32))))

    private fun theEvidencePaymentIsReadable() = getPaymentReturns(SOME_PAYMENT_ID, anInternalPaymentResponse()
        .withPaymentId(SOME_PAYMENT_ID)
        .withCreatedAt("2026-10-15T10:01:00Z")
        .withAmountMinorUnits(199_900L)
        .withBeneficiaryName("Acme Holdings")
        .withBeneficiarySortCode("65-82-37")
        .withBeneficiaryAccountNo("12345678"))
}
