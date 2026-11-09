package com.malai.copilot.acceptance.sad

import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.copilot.acceptance.CopilotAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import java.util.UUID

class CopilotExplanationsControllerTest : CopilotAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val someManagerUserId = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val someCustomerUserId = UUID.fromString("00000000-0000-4000-8000-000000000003")
    private val somePaymentId = UUID.fromString("00000000-0000-4000-8000-0000000000b1")

    @Test
    fun aManagerJwtIsRefusedTheCopilot() = malaiAcceptanceTest {
        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "manager-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someManagerUserId, listOf("manager"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun aCustomerJwtIsRefusedTheCopilot() = malaiAcceptanceTest {
        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "customer-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withCustomerJwtTokenForUserId(someCustomerUserId)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun anUnknownPaymentIsNotAnExplainableSubject() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getPaymentReturnsNotFound(somePaymentId))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "unknown-payment-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
        }
    }

    @Test
    fun anExplanationRequestWithoutAnIdempotencyKeyIsRejected() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getPaymentReturns(somePaymentId, anInternalPaymentResponse().withPaymentId(somePaymentId)))

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun whenTheModelDecisionCannotBeRecordedNoExplanationIsServed() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getPaymentReturns(somePaymentId, anInternalPaymentResponse().withPaymentId(somePaymentId)))
        and(recordModelDecisionFailsWithServerError())

        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "mdr-down-idempotency-key")
                .body("""{"subjectType":"PAYMENT","subjectId":"$somePaymentId"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe INTERNAL_SERVER_ERROR
            body shouldNotContain "claims"
            body shouldNotContain "decisionId"
        }
    }

    @Test
    fun anExplanationRequestWithoutASubjectIsRejected() = malaiAcceptanceTest {
        whenever(copilotServiceReceives(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "no-subject-idempotency-key")
                .body("""{"subjectType":"PAYMENT"}""")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }
}
