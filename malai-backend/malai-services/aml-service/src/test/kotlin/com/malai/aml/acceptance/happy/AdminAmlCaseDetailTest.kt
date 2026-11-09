package com.malai.aml.acceptance.happy

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.support.PersistedAmlCase
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.Companion.aDecisionRecordResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.ContextRefBuilder.Companion.aPaymentContextRef
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000d1")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ab1")

private val FIRST_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade1")
private val SECOND_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade2")
private val SAR_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005a1")

private val FIRST_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000e1")
private val SECOND_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000e2")
private val THIRD_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000e3")

class AdminAmlCaseDetailTest : AmlAcceptanceTest() {

    @Test
    fun theCaseDetailAssemblesEveryAlertWithItsEvidenceAndRulingSoFar() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING,
            listOf(FIRST_ALERT_ID, SECOND_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(decisionRecordReturns(FIRST_ALERT_ID, aStructuringAlertRecord()
            .withRetrievedContext(
                aPaymentContextRef(SECOND_PAYMENT_ID, "b2".repeat(32)),
                aPaymentContextRef(FIRST_PAYMENT_ID, "a1".repeat(32)),
            )
            .withRulingInForce("REJECT")))
        and(decisionRecordReturns(SECOND_ALERT_ID, aCorridorAlertRecord()
            .withRetrievedContext(aPaymentContextRef(THIRD_PAYMENT_ID, "c3".repeat(32)))))
        and(getPaymentReturns(FIRST_PAYMENT_ID, anInternalPaymentResponse()
            .withPaymentId(FIRST_PAYMENT_ID)
            .withCreatedAt("2026-10-15T10:01:00Z")
            .withAmountMinorUnits(199_900L)
            .withBeneficiaryName("Acme Holdings")
            .withBeneficiarySortCode("65-82-37")
            .withBeneficiaryAccountNo("12345678")))
        and(getPaymentReturns(SECOND_PAYMENT_ID, anInternalPaymentResponse()
            .withPaymentId(SECOND_PAYMENT_ID)
            .withCreatedAt("2026-10-15T10:02:00Z")
            .withAmountMinorUnits(199_800L)
            .withBeneficiaryName("Acme Holdings")
            .withBeneficiarySortCode("65-82-37")
            .withBeneficiaryAccountNo("12345678")))
        and(getPaymentReturns(THIRD_PAYMENT_ID, anInternalPaymentResponse()
            .withPaymentId(THIRD_PAYMENT_ID)
            .withRail("SWIFT")
            .withCreatedAt("2026-10-15T10:20:00Z")
            .withAmountMinorUnits(750_000L)
            .withBeneficiaryName("Offshore Trading")
            .withBeneficiaryIban("KY12ABCD00000000000001")
            .withBeneficiaryBic("ABCDKYK1XXX")))

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
                "status" To "INVESTIGATING"
                "openedAt" To "2026-10-15T10:00:00Z"
                "updatedAt" To "2026-10-15T10:00:00Z"
                "alerts" To listOf(
                    json {
                        "decisionId" To FIRST_ALERT_ID.toString()
                        "typology" To "STRUCTURING"
                        "riskScore" To 70
                        "raisedAt" To "2026-10-15T10:05:00Z"
                        "rulingInForce" To "REJECT"
                        "evidence" To listOf(
                            json {
                                "paymentId" To FIRST_PAYMENT_ID.toString()
                                "contentHash" To "a1".repeat(32)
                                "rail" To "FPS"
                                "direction" To "OUTBOUND"
                                "amountMinorUnits" To 199900
                                "currency" To "GBP"
                                "createdAt" To "2026-10-15T10:01:00Z"
                                "beneficiaryName" To "Acme Holdings"
                                "beneficiarySortCode" To "65-82-37"
                                "beneficiaryAccountNo" To "12345678"
                                noExtraProps()
                            },
                            json {
                                "paymentId" To SECOND_PAYMENT_ID.toString()
                                "contentHash" To "b2".repeat(32)
                                "rail" To "FPS"
                                "direction" To "OUTBOUND"
                                "amountMinorUnits" To 199800
                                "currency" To "GBP"
                                "createdAt" To "2026-10-15T10:02:00Z"
                                "beneficiaryName" To "Acme Holdings"
                                "beneficiarySortCode" To "65-82-37"
                                "beneficiaryAccountNo" To "12345678"
                                noExtraProps()
                            },
                        )
                        noExtraProps()
                    },
                    json {
                        "decisionId" To SECOND_ALERT_ID.toString()
                        "typology" To "HIGH_RISK_CORRIDOR"
                        "riskScore" To 80
                        "raisedAt" To "2026-10-15T10:25:00Z"
                        "evidence" To listOf(
                            json {
                                "paymentId" To THIRD_PAYMENT_ID.toString()
                                "contentHash" To "c3".repeat(32)
                                "rail" To "SWIFT"
                                "direction" To "OUTBOUND"
                                "amountMinorUnits" To 750000
                                "currency" To "GBP"
                                "createdAt" To "2026-10-15T10:20:00Z"
                                "beneficiaryName" To "Offshore Trading"
                                "beneficiaryIban" To "KY12ABCD00000000000001"
                                "beneficiaryBic" To "ABCDKYK1XXX"
                                noExtraProps()
                            },
                        )
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun theFirstDispositionOnALinkedAlertMovesTheCaseToInvestigating() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(FIRST_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(decisionRecordReturns(FIRST_ALERT_ID, aStructuringAlertRecord()
            .withRetrievedContext(aPaymentContextRef(FIRST_PAYMENT_ID, "a1".repeat(32)))
            .withRulingInForce("ACCEPT")))
        and(getPaymentReturns(FIRST_PAYMENT_ID, anInternalPaymentResponse()
            .withPaymentId(FIRST_PAYMENT_ID)
            .withCreatedAt("2026-10-15T10:01:00Z")))

        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "status" To "INVESTIGATING"
                "updatedAt" To "2026-10-15T12:00:00Z"
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "INVESTIGATING",
                alertDecisionIds = listOf(FIRST_ALERT_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-10-15T12:00:00Z"),
                closedAt = null,
            )
        }
    }

    @Test
    fun anUnruledCaseStaysOpenAndCarriesNoSarState() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(FIRST_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(decisionRecordReturns(FIRST_ALERT_ID, aStructuringAlertRecord()
            .withRetrievedContext(aPaymentContextRef(FIRST_PAYMENT_ID, "a1".repeat(32)))))
        and(getPaymentReturns(FIRST_PAYMENT_ID, anInternalPaymentResponse()
            .withPaymentId(FIRST_PAYMENT_ID)
            .withCreatedAt("2026-10-15T10:01:00Z")))

        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "status" To "OPEN"
                "updatedAt" To "2026-10-15T10:00:00Z"
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            status shouldBe "OPEN"
            updatedAt shouldBe Instant.parse("2026-10-15T10:00:00Z")
        }
    }

    @Test
    fun aReportedCaseCarriesItsSarStateAndTheAttachedNarrative() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.CLOSED_REPORTED, listOf(FIRST_ALERT_ID),
            "2026-10-15T10:00:00Z", SAR_DECISION_ID,
        ))
        and(decisionRecordReturns(FIRST_ALERT_ID, aStructuringAlertRecord()
            .withRetrievedContext(aPaymentContextRef(FIRST_PAYMENT_ID, "a1".repeat(32)))
            .withRulingInForce("ACCEPT")))
        and(decisionRecordReturns(SAR_DECISION_ID, aDecisionRecordResponse()
            .withDecisionId(SAR_DECISION_ID)
            .withCorrelationType("CASE")
            .withCorrelationId(SOME_CASE_ID.toString())
            .withModelId("malai-sar-template")
            .withRulingInForce("EDIT")))
        and(getPaymentReturns(FIRST_PAYMENT_ID, anInternalPaymentResponse()
            .withPaymentId(FIRST_PAYMENT_ID)
            .withCreatedAt("2026-10-15T10:01:00Z")))

        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "status" To "CLOSED_REPORTED"
                "sar" To json {
                    "decisionId" To SAR_DECISION_ID.toString()
                    "rulingInForce" To "EDIT"
                    "narrative" To "the filed narrative"
                    noExtraProps()
                }
            }
        }
    }

    private fun aStructuringAlertRecord(): DecisionRecordResponseBuilder = anAlertRecord(FIRST_ALERT_ID)
        .withTs("2026-10-15T10:05:00Z")
        .withVerdict(mapOf("typology" to "STRUCTURING", "riskScore" to 70))

    private fun aCorridorAlertRecord(): DecisionRecordResponseBuilder = anAlertRecord(SECOND_ALERT_ID)
        .withTs("2026-10-15T10:25:00Z")
        .withVerdict(mapOf("typology" to "HIGH_RISK_CORRIDOR", "riskScore" to 80))

    private fun anAlertRecord(decisionId: UUID): DecisionRecordResponseBuilder = aDecisionRecordResponse()
        .withDecisionId(decisionId)
        .withCorrelationType("ACCOUNT")
        .withCorrelationId(SOME_ACCOUNT_ID.toString())
}
