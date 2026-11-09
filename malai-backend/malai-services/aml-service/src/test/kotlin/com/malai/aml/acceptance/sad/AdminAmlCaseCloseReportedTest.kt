package com.malai.aml.acceptance.sad

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.support.PersistedAmlCase
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
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.time.Instant
import java.util.UUID

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")
private val SOME_CUSTOMER_USER_ID: UUID = UUID.fromString("cc000000-0000-4000-9000-000000000001")

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000f8")
private val UNKNOWN_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000fe")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ac8")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000adf8")
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000f8")
private val SOME_SAR_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000005d8")
private val SOME_RULING_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ae18")

private const val THE_GATE_URL_FOR_THE_SAR =
    "/mdr-service/api/v1/internal/mdr/decisions/00000000-0000-4000-9000-0000000005d8/actionable-output"

class AdminAmlCaseCloseReportedTest : AmlAcceptanceTest() {

    @Test
    fun aRejectRulingInForceRefusesTheCloseAndTheCaseStillShowsThatReject() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aCaseAwaitingItsSarRuling())
        and(actionableOutputIsRefused(SOME_SAR_DECISION_ID))
        and(theSarRecordIsReadable(rulingInForce = "REJECT"))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(theCloseRequest("a-rejected-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6989"
                noExtraProps()
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe anUntouchedCase()
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }

        whenever(amlServiceReceives(theDetailRequest()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "status" To "INVESTIGATING"
                "sar" To json {
                    "decisionId" To SOME_SAR_DECISION_ID.toString()
                    "rulingInForce" To "REJECT"
                    noExtraProps()
                }
            }
        }
    }

    @Test
    fun aDraftStillAwaitingItsRulingRefusesTheCloseJustAsARejectDoes() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aCaseAwaitingItsSarRuling())
        and(actionableOutputIsRefused(SOME_SAR_DECISION_ID))
        and(theSarRecordIsReadable(rulingInForce = null))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(theCloseRequest("an-unruled-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6989"
                noExtraProps()
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe anUntouchedCase()
        }

        whenever(amlServiceReceives(theDetailRequest()))

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
    fun aCaseWithNoSarDraftAtAllIsRefusedWithoutTheGateEverBeingConsulted() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))

        whenever(amlServiceReceives(theCloseRequest("an-undrafted-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6989"
                noExtraProps()
            }
        }

        and(amlServiceMadeTheFollowingHttpRequests { url == THE_GATE_URL_FOR_THE_SAR }) {
            this shouldHaveSize 0
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            status shouldBe "INVESTIGATING"
            sarNarrative shouldBe null
            closedAt shouldBe null
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun outputTheCaseCannotFileLeavesTheCaseExactlyWhereItWas() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aCaseAwaitingItsSarRuling())
        and(actionableOutputReturns(SOME_SAR_DECISION_ID, anActionableOutputResponse()
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withRuling("EDIT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput("""{"narrative":"   "}""")))

        whenever(amlServiceReceives(theCloseRequest("an-unfileable-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6989"
                noExtraProps()
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe anUntouchedCase()
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun closeAsReportedOnAnAlreadyClosedCaseReturns409() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(SOME_ALERT_ID),
            "2026-10-15T10:00:00Z",
        ))

        whenever(amlServiceReceives(theCloseRequest("an-already-closed-report-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6988"
                noExtraProps()
            }
        }
    }

    @Test
    fun closeAsReportedOnAnUnknownCaseReturns404() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())

        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$UNKNOWN_CASE_ID/close-reported")
                .header("Idempotency-Key", "an-unknown-case-report-key")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8974"
                noExtraProps()
            }
        }
    }

    @Test
    fun closeAsReportedWithoutAnIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(aCaseAwaitingItsSarRuling())

        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-reported")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun closeAsReportedWithoutAJwtReturns401() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-reported")
                .header("Idempotency-Key", "an-unauthenticated-report-key"),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun aCustomerJwtOnCloseAsReportedReturns403() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-reported")
                .header("Idempotency-Key", "a-customer-report-key")
                .withCustomerJwtTokenForUserId(SOME_CUSTOMER_USER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    private fun theCloseRequest(idempotencyKey: String) = post()
        .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-reported")
        .header("Idempotency-Key", idempotencyKey)
        .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin"))

    private fun theDetailRequest() = get()
        .url("/api/v1/admin/aml/cases/$SOME_CASE_ID")
        .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin"))

    private fun aCaseAwaitingItsSarRuling() = anAmlCaseExists(
        SOME_CASE_ID,
        SOME_ACCOUNT_ID,
        AmlCaseStatus.INVESTIGATING,
        listOf(SOME_ALERT_ID),
        "2026-10-15T10:00:00Z",
        sarDecisionId = SOME_SAR_DECISION_ID,
    )

    private fun anUntouchedCase() = PersistedAmlCase(
        caseId = SOME_CASE_ID,
        accountId = SOME_ACCOUNT_ID,
        status = "INVESTIGATING",
        alertDecisionIds = listOf(SOME_ALERT_ID),
        sarDecisionId = SOME_SAR_DECISION_ID,
        sarNarrative = null,
        openedAt = Instant.parse("2026-10-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-15T10:00:00Z"),
        closedAt = null,
    )

    private fun theSarRecordIsReadable(rulingInForce: String?) =
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
        .withCreatedAt("2026-10-15T10:01:00Z"))
}
