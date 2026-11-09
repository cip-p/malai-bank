package com.malai.aml.acceptance.sad

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.support.PersistedAmlCase
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.Companion.aDecisionRecordResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.ContextRefBuilder.Companion.aPaymentContextRef
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.time.Instant
import java.util.UUID

private const val MDR_DECISIONS_URL = "/mdr-service/api/v1/internal/mdr/decisions"
private const val A_PAYMENT_THE_PLATFORM_NEVER_RETRIEVED = "00000000-0000-4000-8000-00000000dead"
private const val THE_ARMED_UNGROUNDED_AMOUNT_MINOR_UNITS = 314_159L

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")
private val SOME_CUSTOMER_USER_ID: UUID = UUID.fromString("cc000000-0000-4000-9000-000000000001")

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000006c1")
private val UNKNOWN_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000006cf")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000006a0")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade6")
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-00000000006a")

private val SOME_SAR_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000006d1")
private val SOME_SAR_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000006d2")

class AdminAmlSarDraftTest : AmlAcceptanceTest() {

    @Test
    fun aDraftCitingOutsideTheTimelineIsRecordedAsAFailureAndRefusedRatherThanServed() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_SAR_DECISION_ID, SOME_SAR_ENTRY_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable(THE_ARMED_UNGROUNDED_AMOUNT_MINOR_UNITS))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(SOME_SAR_ENTRY_ID)
            .withDecisionId(SOME_SAR_DECISION_ID)
            .withSeq(1L)
            .withOutcome("COMPLETED")))

        whenever(amlServiceReceives(theDraftRequest("an-ungrounded-sar-draft-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To SOME_SAR_DECISION_ID.toString()
                "outcome" To "COMPLETED"
                "refusalCode" To "GROUNDING_FAILED"
                noExtraProps()
            }
        }

        and(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            testingObjectMapper.readTree(body).run {
                at("/outcome").asString() shouldBe "COMPLETED"
                at("/rawOutput").asString() shouldContain A_PAYMENT_THE_PLATFORM_NEVER_RETRIEVED
                has("verdict") shouldBe false
                has("errorDetail") shouldBe false
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            sarDecisionId shouldBe SOME_SAR_DECISION_ID
            sarNarrative shouldBe null
        }
    }

    @Test
    fun aFailureToAppendTheModelDecisionFailsTheWholeDraftRequest() = malaiAcceptanceTest {
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
        and(recordModelDecisionFailsWithServerError())

        whenever(amlServiceReceives(theDraftRequest("an-unrecordable-sar-draft-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe INTERNAL_SERVER_ERROR
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "INVESTIGATING",
                alertDecisionIds = listOf(SOME_ALERT_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:00:00Z"),
                updatedAt = Instant.parse("2026-10-15T10:00:00Z"),
                closedAt = null,
            )
        }
    }

    @Test
    fun aDraftRequestOnAnAlreadyClosedCaseReturns409() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(SOME_ALERT_ID),
            "2026-10-15T10:00:00Z",
        ))

        whenever(amlServiceReceives(theDraftRequest("a-closed-case-sar-draft-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6988"
                noExtraProps()
            }
        }
    }

    @Test
    fun aDraftRequestOnAnUnknownCaseReturns404() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())

        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$UNKNOWN_CASE_ID/sar-draft")
                .header("Idempotency-Key", "an-unknown-case-sar-draft-key")
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
    fun aDraftRequestWithoutAnIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))

        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/sar-draft")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun aDraftRequestWithoutAJwtReturns401() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/sar-draft")
                .header("Idempotency-Key", "an-unauthenticated-sar-draft-key"),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun aCustomerJwtOnTheDraftRequestReturns403() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/sar-draft")
                .header("Idempotency-Key", "a-customer-sar-draft-key")
                .withCustomerJwtTokenForUserId(SOME_CUSTOMER_USER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    private fun theDraftRequest(idempotencyKey: String) = post()
        .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/sar-draft")
        .header("Idempotency-Key", idempotencyKey)
        .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin"))

    private fun theAlertRecordIsReadable() = decisionRecordReturns(SOME_ALERT_ID, aDecisionRecordResponse()
        .withDecisionId(SOME_ALERT_ID)
        .withCorrelationType("ACCOUNT")
        .withCorrelationId(SOME_ACCOUNT_ID.toString())
        .withTs("2026-10-15T10:05:00Z")
        .withVerdict(mapOf("typology" to "STRUCTURING", "riskScore" to 70))
        .withRetrievedContext(aPaymentContextRef(SOME_PAYMENT_ID, "a1".repeat(32))))

    private fun theEvidencePaymentIsReadable(amountMinorUnits: Long = 199_900L) =
        getPaymentReturns(SOME_PAYMENT_ID, anInternalPaymentResponse()
            .withPaymentId(SOME_PAYMENT_ID)
            .withCreatedAt("2026-10-15T10:01:00Z")
            .withAmountMinorUnits(amountMinorUnits))
}
