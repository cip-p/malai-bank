package com.malai.test.e2e

import tools.jackson.module.kotlin.readValue
import com.malai.test.acceptance.eventually
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext

/**
 * E2E-test helpers for hitting the malai-stub container directly. malai-stub simulates the
 * outside-world bank (CoP federation, FPS / SWIFT outbound scheme settlement, FPS / BACS
 * inbound). Tests use these helpers to drive the stub's `/scenarios/inbound/{fps,bacs}` endpoints
 * when exercising inbound flows. The stub POSTs a scheme-shaped inbound notification to the
 * matching rail gateway over HTTP — fps-gateway for FPS, bacs-gateway for BACS — and the gateway
 * produces `payments-{fps,bacs}-event-received`, which payments-service consumes via its own
 * `@SqsListener`. The stub itself holds no SQS dependency.
 */
interface WithMalaiStub {

    val malaiStubHttpClient: HttpClientStub

    fun malaiStubReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        malaiStubHttpClient.sends(httpRequest.build())

    fun malaiStubReceivesFpsInbound(
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
        beneficiaryName: String,
        originatorSortCode: String,
        originatorAccountNumber: String,
        originatorName: String,
        amountMinorUnits: Long,
        currency: String = "GBP",
        description: String? = null,
    ): Action<ActionContext> =
        malaiStubReceivesInboundDemo(
            path = "/scenarios/inbound/fps",
            beneficiarySortCode = beneficiarySortCode,
            beneficiaryAccountNumber = beneficiaryAccountNumber,
            beneficiaryName = beneficiaryName,
            originatorSortCode = originatorSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
        )

    fun malaiStubReceivesBacsInbound(
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
        beneficiaryName: String,
        originatorSortCode: String,
        originatorAccountNumber: String,
        originatorName: String,
        amountMinorUnits: Long,
        currency: String = "GBP",
        description: String? = null,
    ): Action<ActionContext> =
        malaiStubReceivesInboundDemo(
            path = "/scenarios/inbound/bacs",
            beneficiarySortCode = beneficiarySortCode,
            beneficiaryAccountNumber = beneficiaryAccountNumber,
            beneficiaryName = beneficiaryName,
            originatorSortCode = originatorSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
        )

    private fun malaiStubReceivesInboundDemo(
        path: String,
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
        beneficiaryName: String,
        originatorSortCode: String,
        originatorAccountNumber: String,
        originatorName: String,
        amountMinorUnits: Long,
        currency: String,
        description: String?,
    ): Action<ActionContext> =
        malaiStubHttpClient.sends(
            post()
                .url(path)
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "beneficiarySortCode" to beneficiarySortCode,
                    "beneficiaryAccountNumber" to beneficiaryAccountNumber,
                    "beneficiaryName" to beneficiaryName,
                    "originatorSortCode" to originatorSortCode,
                    "originatorAccountNumber" to originatorAccountNumber,
                    "originatorName" to originatorName,
                    "amountMinorUnits" to amountMinorUnits,
                    "currency" to currency,
                    "description" to description,
                ))
                .build()
        )

    private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))

    /**
     * Polls the stub's `GET /test-hooks/fps/returns` until it has recorded an FPS return-to-sender whose
     * creditor (the un-creditable beneficiary) carries [beneficiaryAccountNumber]. Drives the
     * unknown-beneficiary inbound path: the gateway produces `payments-fps-event-received`,
     * payments-service finds no account and emits `payments-fps-command-return`, the gateway POSTs
     * a scheme-shaped return-to-sender, and the stub records it here. Uses the preconditions client
     * so the poll itself does not render as a Client → MalaiStub arrow.
     */
    fun theMalaiStubReceivedAnFpsReturnToSenderForBeneficiary(beneficiaryAccountNumber: String) = CallableAction {
        eventually {
            check(fetchReceivedReturns().any { it.creditor?.accountNumber == beneficiaryAccountNumber }) {
                "malai-stub has not recorded an FPS return-to-sender for beneficiary $beneficiaryAccountNumber"
            }
        }
    }

    private fun fetchReceivedReturns(): List<RecordedReturnToSender> {
        val body = MalaiE2ETestContext.preconditionsMalaiStubHttpClient.get()
            .uri("/test-hooks/fps/returns")
            .retrieve()
            .body(String::class.java) ?: "[]"
        return testingObjectMapper.readValue(body)
    }
}

data class RecordedReturnToSender(
    val endToEndId: String,
    val creditor: RecordedReturnToSenderParty?,
    val reasonCode: String,
)

data class RecordedReturnToSenderParty(
    val sortCode: String?,
    val accountNumber: String?,
    val name: String?,
)
