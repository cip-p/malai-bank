package com.malai.client.malai.mdr

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.mdr.builders.response.ActionableOutputResponseBuilder.Companion.anActionableOutputResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.Companion.aDecisionRecordResponse
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder.ContextRefBuilder.Companion.aPaymentContextRef
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.mdr.model.ActionableOutputResponse
import com.malai.client.malai.mdr.model.DecisionRecordResponse
import com.malai.client.malai.mdr.model.ModelDecisionOutcome.COMPLETED
import com.malai.client.malai.mdr.model.ModelDecisionOutcome.ERRORED
import com.malai.client.malai.mdr.model.ModelDecisionOutcome.TIMED_OUT
import com.malai.client.malai.mdr.model.RecordModelDecisionRequest
import com.malai.client.malai.mdr.model.RecordModelDecisionResponse
import com.malai.client.malai.mdr.model.RetrievedContextRef
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import java.util.UUID

@MalaiClientSpringTest
class MdrClientTest : MalaiClientTest(), WithMdrApi {

    @Autowired
    private lateinit var mdrClient: MdrClient

    private var lastRecordResponse: RecordModelDecisionResponse? = null
    private var lastThrowable: Throwable? = null
    private var actionableOutputWasRead = false
    private var lastActionableOutput: ActionableOutputResponse? = null
    private var lastDecisionRecord: DecisionRecordResponse? = null

    private val someEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000e1")
    private val someDecisionId = UUID.fromString("00000000-0000-4000-7000-0000000000d2")
    private val someRulingEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000a3")

    @Test
    fun recordsACompletedModelDecision() {
        given(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(42L)
            .withOutcome("COMPLETED")))

        whenever(clientCallsRecordModelDecision("some-service-token", aCompletedRequest()))

        then(theLastRecordResponse()) {
            this shouldBe RecordModelDecisionResponse(someEntryId, someDecisionId, 42L, "COMPLETED")
        }
        and(madeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To somePaymentUid().toString()
                "advisory" To false
                "modelId" To "malai-screening-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "payment-screening"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
                "renderedPrompt" To "Assess outbound payment ${somePaymentUid()} for screening."
                "retrievedContext" To listOf(
                    json {
                        "type" To "ACCOUNT"
                        "id" To someAccountUid().toString()
                        "contentHash" To "1a2b3c"
                        noExtraProps()
                    },
                    json {
                        "type" To "WATCHLIST"
                        "id" To "watchlist-v1"
                        "contentHash" To "4d5e6f"
                        noExtraProps()
                    }
                )
                "outcome" To "COMPLETED"
                "rawOutput" To "DECLINE: amount at or above threshold."
                "verdict" To json {
                    "decision" To "DECLINE"
                    "riskScore" To 82
                    noExtraProps()
                }
                "latencyMs" To 12
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }
    }

    @Test
    fun recordsATimedOutModelDecisionWithoutOutputFields() {
        given(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(43L)
            .withOutcome("TIMED_OUT")))

        whenever(clientCallsRecordModelDecision("some-service-token", aTimedOutRequest()))

        then(theLastRecordResponse()) {
            this shouldBe RecordModelDecisionResponse(someEntryId, someDecisionId, 43L, "TIMED_OUT")
        }
        and(madeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To somePaymentUid().toString()
                "advisory" To false
                "modelId" To "malai-screening-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "payment-screening"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
                "renderedPrompt" To "Assess outbound payment ${somePaymentUid()} for screening."
                "retrievedContext" To listOf(
                    json {
                        "type" To "ACCOUNT"
                        "id" To someAccountUid().toString()
                        "contentHash" To "1a2b3c"
                        noExtraProps()
                    },
                    json {
                        "type" To "WATCHLIST"
                        "id" To "watchlist-v1"
                        "contentHash" To "4d5e6f"
                        noExtraProps()
                    }
                )
                "outcome" To "TIMED_OUT"
                "latencyMs" To 5000
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }
    }

    @Test
    fun recordsAnErroredModelDecisionWithErrorDetail() {
        given(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(44L)
            .withOutcome("ERRORED")))

        whenever(clientCallsRecordModelDecision("some-service-token", anErroredRequest()))

        then(theLastRecordResponse()) {
            this shouldBe RecordModelDecisionResponse(someEntryId, someDecisionId, 44L, "ERRORED")
        }
        and(madeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To somePaymentUid().toString()
                "advisory" To false
                "modelId" To "malai-screening-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "payment-screening"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
                "renderedPrompt" To "Assess outbound payment ${somePaymentUid()} for screening."
                "retrievedContext" To listOf(
                    json {
                        "type" To "ACCOUNT"
                        "id" To someAccountUid().toString()
                        "contentHash" To "1a2b3c"
                        noExtraProps()
                    },
                    json {
                        "type" To "WATCHLIST"
                        "id" To "watchlist-v1"
                        "contentHash" To "4d5e6f"
                        noExtraProps()
                    }
                )
                "outcome" To "ERRORED"
                "errorDetail" To "decider raised IllegalStateException"
                "latencyMs" To 12
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }
    }

    @Test
    fun recordsAnAdvisoryModelDecision() {
        given(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(someEntryId)
            .withDecisionId(someDecisionId)
            .withSeq(45L)
            .withOutcome("COMPLETED")))

        whenever(clientCallsRecordModelDecision("some-service-token", aCompletedRequest().copy(advisory = true)))

        then(theLastRecordResponse()) {
            this shouldBe RecordModelDecisionResponse(someEntryId, someDecisionId, 45L, "COMPLETED")
        }
        and(madeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To somePaymentUid().toString()
                "advisory" To true
                "modelId" To "malai-screening-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "payment-screening"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
                "renderedPrompt" To "Assess outbound payment ${somePaymentUid()} for screening."
                "retrievedContext" To listOf(
                    json {
                        "type" To "ACCOUNT"
                        "id" To someAccountUid().toString()
                        "contentHash" To "1a2b3c"
                        noExtraProps()
                    },
                    json {
                        "type" To "WATCHLIST"
                        "id" To "watchlist-v1"
                        "contentHash" To "4d5e6f"
                        noExtraProps()
                    }
                )
                "outcome" To "COMPLETED"
                "rawOutput" To "DECLINE: amount at or above threshold."
                "verdict" To json {
                    "decision" To "DECLINE"
                    "riskScore" To 82
                    noExtraProps()
                }
                "latencyMs" To 12
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }
    }

    @Test
    fun surfacesAnMdrWriteFailureAsAClientResponseException() {
        given(recordModelDecisionFailsWithServerError())

        whenever(clientAttemptsToRecordModelDecision("some-service-token", aCompletedRequest()))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    @Test
    fun readsTheActionableOutputServedUnderAnAcceptRuling() {
        given(actionableOutputReturns(someDecisionId, anActionableOutputResponse()
            .withDecisionId(someDecisionId)
            .withRuling("ACCEPT")
            .withRulingEntryId(someRulingEntryId)
            .withActionableOutput("DECLINE: amount at or above threshold.")))

        whenever(clientCallsActionableOutput("some-service-token", someDecisionId))

        then(theLastActionableOutput()) {
            this shouldBe ActionableOutputResponse(
                decisionId = someDecisionId,
                ruling = "ACCEPT",
                rulingEntryId = someRulingEntryId,
                actionableOutput = "DECLINE: amount at or above threshold.",
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions/$someDecisionId/actionable-output"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    @Test
    fun readsTheEditedOutputServedUnderAnEditRuling() {
        given(actionableOutputReturns(someDecisionId, anActionableOutputResponse()
            .withDecisionId(someDecisionId)
            .withRuling("EDIT")
            .withRulingEntryId(someRulingEntryId)
            .withActionableOutput("APPROVE: reviewed and cleared.")))

        whenever(clientCallsActionableOutput("some-service-token", someDecisionId))

        then(theLastActionableOutput()) {
            this shouldBe ActionableOutputResponse(
                decisionId = someDecisionId,
                ruling = "EDIT",
                rulingEntryId = someRulingEntryId,
                actionableOutput = "APPROVE: reviewed and cleared.",
            )
        }
    }

    @Test
    fun aGateConflictIsNoActionableOutputRatherThanAFailure() {
        given(actionableOutputIsRefused(someDecisionId))

        whenever(clientCallsActionableOutput("some-service-token", someDecisionId))

        then(theActionableOutputRead()) {
            this.shouldBeNull()
        }
        and(madeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions/$someDecisionId/actionable-output"
        }) {
            method shouldBe GET
        }
    }

    @Test
    fun surfacesAGateReadFailureAsAClientResponseException() {
        given(actionableOutputFailsWithServerError(someDecisionId))

        whenever(clientAttemptsToReadActionableOutput("some-service-token", someDecisionId))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    @Test
    fun readsADecisionRecordWithItsVerdictContextRefsAndRulingInForce() {
        given(decisionRecordReturns(someDecisionId, aDecisionRecordResponse()
            .withDecisionId(someDecisionId)
            .withEntryId(someEntryId)
            .withSeq(7L)
            .withTs("2026-10-15T10:30:00Z")
            .withCorrelationId(someAccountUid().toString())
            .withVerdict(mapOf("typology" to "STRUCTURING", "riskScore" to 70))
            .withRetrievedContext(aPaymentContextRef(somePaymentUid(), "1a2b3c"))
            .withRulingInForce("REJECT")))

        whenever(clientCallsDecisionRecord("some-service-token", someDecisionId))

        then(theLastDecisionRecord()) {
            this shouldBe DecisionRecordResponse(
                decisionId = someDecisionId,
                entryId = someEntryId,
                seq = 7L,
                ts = "2026-10-15T10:30:00Z",
                outcome = "COMPLETED",
                advisory = false,
                correlationType = "ACCOUNT",
                correlationId = someAccountUid().toString(),
                modelId = "malai-aml-rules",
                modelVersion = "1",
                verdict = mapOf("typology" to "STRUCTURING", "riskScore" to 70),
                retrievedContext = listOf(
                    RetrievedContextRef(type = "PAYMENT", id = somePaymentUid().toString(), contentHash = "1a2b3c"),
                ),
                errorDetail = null,
                rulingInForce = "REJECT",
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/mdr-service/api/v1/internal/mdr/decisions/$someDecisionId"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    @Test
    fun readsADecisionRecordStillAwaitingItsFirstRuling() {
        given(decisionRecordReturns(someDecisionId, aDecisionRecordResponse()
            .withDecisionId(someDecisionId)
            .withEntryId(someEntryId)
            .withCorrelationId(someAccountUid().toString())))

        whenever(clientCallsDecisionRecord("some-service-token", someDecisionId))

        then(theLastDecisionRecord()) {
            rulingInForce.shouldBeNull()
            verdict.shouldBeNull()
            retrievedContext.shouldBeNull()
        }
    }

    @Test
    fun surfacesAnUnknownDecisionRecordAsAClientResponseException() {
        given(decisionRecordIsNotFound(someDecisionId))

        whenever(clientAttemptsToReadDecisionRecord("some-service-token", someDecisionId))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    private fun aCompletedRequest() = RecordModelDecisionRequest(
        entryId = someEntryId,
        decisionId = someDecisionId,
        correlationType = "PAYMENT",
        correlationId = somePaymentUid().toString(),
        modelId = "malai-screening-rules",
        modelVersion = "1",
        promptTemplateId = "payment-screening",
        promptTemplateVersion = "v1",
        promptTemplateHash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        renderedPrompt = "Assess outbound payment ${somePaymentUid()} for screening.",
        retrievedContext = listOf(
            RetrievedContextRef(type = "ACCOUNT", id = someAccountUid().toString(), contentHash = "1a2b3c"),
            RetrievedContextRef(type = "WATCHLIST", id = "watchlist-v1", contentHash = "4d5e6f"),
        ),
        outcome = COMPLETED,
        rawOutput = "DECLINE: amount at or above threshold.",
        verdict = mapOf("decision" to "DECLINE", "riskScore" to 82),
        latencyMs = 12L,
        costMicros = 0L,
        costCurrency = "GBP",
    )

    private fun aTimedOutRequest() = aCompletedRequest().copy(
        outcome = TIMED_OUT,
        rawOutput = null,
        verdict = null,
        latencyMs = 5000L,
    )

    private fun clientCallsRecordModelDecision(
        serviceToken: String,
        request: RecordModelDecisionRequest,
    ) = Action<ActionContext> {
        lastRecordResponse = mdrClient.recordModelDecision(serviceToken, request)
    }

    private fun anErroredRequest() = aCompletedRequest().copy(
        outcome = ERRORED,
        rawOutput = null,
        verdict = null,
        errorDetail = "decider raised IllegalStateException",
    )

    private fun clientAttemptsToRecordModelDecision(
        serviceToken: String,
        request: RecordModelDecisionRequest,
    ) = Action<ActionContext> {
        lastThrowable = runCatching { mdrClient.recordModelDecision(serviceToken, request) }.exceptionOrNull()
    }

    private fun clientCallsActionableOutput(serviceToken: String, decisionId: UUID) = Action<ActionContext> {
        lastActionableOutput = mdrClient.actionableOutput(serviceToken, decisionId)
        actionableOutputWasRead = true
    }

    private fun clientAttemptsToReadActionableOutput(serviceToken: String, decisionId: UUID) = Action<ActionContext> {
        lastThrowable = runCatching { mdrClient.actionableOutput(serviceToken, decisionId) }.exceptionOrNull()
    }

    private fun theLastRecordResponse() = StateCollector { _ ->
        lastRecordResponse ?: error("No record-model-decision response captured")
    }

    private fun theLastActionableOutput() = StateCollector { _ ->
        lastActionableOutput ?: error("No actionable-output response captured")
    }

    private fun theActionableOutputRead() = StateCollector<ActionableOutputResponse?> { _ ->
        if (!actionableOutputWasRead) error("The gate was never read")
        lastActionableOutput
    }

    private fun clientCallsDecisionRecord(serviceToken: String, decisionId: UUID) = Action<ActionContext> {
        lastDecisionRecord = mdrClient.decisionRecord(serviceToken, decisionId)
    }

    private fun clientAttemptsToReadDecisionRecord(serviceToken: String, decisionId: UUID) = Action<ActionContext> {
        lastThrowable = runCatching { mdrClient.decisionRecord(serviceToken, decisionId) }.exceptionOrNull()
    }

    private fun theLastDecisionRecord() = StateCollector { _ ->
        lastDecisionRecord ?: error("No decision-record response captured")
    }

    private fun theLastThrowable() = StateCollector { _ ->
        lastThrowable ?: error("No throwable captured")
    }
}
