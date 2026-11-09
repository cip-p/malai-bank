package com.malai.aml.acceptance.happy

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.support.PersistedAmlCase
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.Companion.aDecisionRecordResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.ContextRefBuilder.Companion.aPaymentContextRef
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000f1")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ac1")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000adf1")
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000f1")
private val SOME_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa9")

class AdminAmlCaseCloseDismissedTest : AmlAcceptanceTest() {

    @Test
    fun aCaseWithNoAlertRulingStandingClosesAsDismissed() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(actionableOutputIsRefused(SOME_ALERT_ID))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-dismissed")
                .header("Idempotency-Key", "some-dismiss-key")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "caseId" To SOME_CASE_ID.toString()
                "status" To "CLOSED_DISMISSED"
                "updatedAt" To "2026-10-15T12:00:00Z"
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "CLOSED_DISMISSED",
                alertDecisionIds = listOf(SOME_ALERT_ID),
                sarDecisionId = null,
                sarNarrative = null,
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
            this[0]["action"] shouldBe "AML_CASE_CLOSED_DISMISSED"
            this[0]["resource_type"] shouldBe "AmlCase"
            this[0]["resource_id"] shouldBe SOME_CASE_ID.toString()
            this[0]["before_state"] shouldBe """{"status": "INVESTIGATING"}"""
            this[0]["after_state"] shouldBe
                """{"status": "CLOSED_DISMISSED", "alertDecisionIds": ["$SOME_ALERT_ID"]}"""
        }
    }

    @Test
    fun aRetriedCloseEnactsExactlyOnce() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(actionableOutputIsRefused(SOME_ALERT_ID))
        and(theAlertRecordIsReadable())
        and(theEvidencePaymentIsReadable())

        whenever(amlServiceReceives(theCloseRequest("a-retried-dismiss-key")))
        whenever(amlServiceReceives(theCloseRequest("a-retried-dismiss-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "caseId" To SOME_CASE_ID.toString()
                "status" To "CLOSED_DISMISSED"
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "AML_CASE_CLOSED_DISMISSED"
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            status shouldBe "CLOSED_DISMISSED"
            closedAt shouldBe Instant.parse("2026-10-15T12:00:00Z")
        }
    }

    private fun theCloseRequest(idempotencyKey: String) = post()
        .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-dismissed")
        .header("Idempotency-Key", idempotencyKey)
        .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin"))

    private fun theAlertRecordIsReadable() = decisionRecordReturns(SOME_ALERT_ID, aDecisionRecordResponse()
        .withDecisionId(SOME_ALERT_ID)
        .withCorrelationType("ACCOUNT")
        .withCorrelationId(SOME_ACCOUNT_ID.toString())
        .withTs("2026-10-15T10:05:00Z")
        .withVerdict(mapOf("typology" to "STRUCTURING", "riskScore" to 70))
        .withRetrievedContext(aPaymentContextRef(SOME_PAYMENT_ID, "a1".repeat(32))))

    private fun theEvidencePaymentIsReadable() = getPaymentReturns(SOME_PAYMENT_ID, anInternalPaymentResponse()
        .withPaymentId(SOME_PAYMENT_ID)
        .withCreatedAt("2026-10-15T10:01:00Z"))
}
