package com.malai.aml.acceptance.sad

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.support.PersistedAmlCase
import com.malai.client.malai.mdr.builders.response.ActionableOutputResponseBuilder.Companion.anActionableOutputResponse
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
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.time.Instant
import java.util.UUID

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")
private val SOME_CUSTOMER_USER_ID: UUID = UUID.fromString("cc000000-0000-4000-9000-000000000001")

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000f2")
private val UNKNOWN_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000ff")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ac2")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000adf2")
private val SOME_RULING_ENTRY_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ae12")

class AdminAmlCaseDetailAndClosureTest : AmlAcceptanceTest() {

    @Test
    fun theDetailOfAnUnknownCaseReturns404() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())

        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases/$UNKNOWN_CASE_ID")
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
    fun aCustomerJwtOnTheCaseDetailReturns403() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID")
                .withCustomerJwtTokenForUserId(SOME_CUSTOMER_USER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun closeAsDismissedIsRefusedWhileAnAcceptedAlertStands() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))
        and(actionableOutputReturns(SOME_ALERT_ID, anActionableOutputResponse()
            .withDecisionId(SOME_ALERT_ID)
            .withRuling("ACCEPT")
            .withRulingEntryId(SOME_RULING_ENTRY_ID)
            .withActionableOutput("""{"typology":"STRUCTURING","riskScore":70}""")))

        whenever(amlServiceReceives(theCloseRequest("a-refused-dismiss-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6990"
                noExtraProps()
            }
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

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun closeAsDismissedOnAnAlreadyClosedCaseReturns409() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T12:00:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(SOME_ALERT_ID),
            "2026-10-15T10:00:00Z",
        ))

        whenever(amlServiceReceives(theCloseRequest("an-already-closed-dismiss-key")))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6988"
                noExtraProps()
            }
        }
    }

    @Test
    fun closeAsDismissedOnAnUnknownCaseReturns404() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())

        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$UNKNOWN_CASE_ID/close-dismissed")
                .header("Idempotency-Key", "an-unknown-case-dismiss-key")
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
    fun closeAsDismissedWithoutAnIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(
            SOME_CASE_ID, SOME_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SOME_ALERT_ID), "2026-10-15T10:00:00Z",
        ))

        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-dismissed")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun closeAsDismissedWithoutAJwtReturns401() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-dismissed")
                .header("Idempotency-Key", "an-unauthenticated-dismiss-key"),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun aCustomerJwtOnCloseAsDismissedReturns403() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            post()
                .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-dismissed")
                .header("Idempotency-Key", "a-customer-dismiss-key")
                .withCustomerJwtTokenForUserId(SOME_CUSTOMER_USER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    private fun theCloseRequest(idempotencyKey: String) = post()
        .url("/api/v1/admin/aml/cases/$SOME_CASE_ID/close-dismissed")
        .header("Idempotency-Key", idempotencyKey)
        .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin"))
}
