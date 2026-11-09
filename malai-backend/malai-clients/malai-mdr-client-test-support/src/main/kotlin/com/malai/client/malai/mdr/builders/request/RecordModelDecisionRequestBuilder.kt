package com.malai.client.malai.mdr.builders.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class RecordModelDecisionRequestBuilder private constructor() : TestBuilder {

    private var entryId: String? = null
    private var decisionId: String? = null
    private var correlationType: String? = null
    private var correlationId: String? = null
    private var advisory: Boolean? = null
    private var modelId: String? = null
    private var modelVersion: String? = null
    private var promptTemplateId: String? = null
    private var promptTemplateVersion: String? = null
    private var promptTemplateHash: String? = null
    private var renderedPrompt: String? = null
    private var retrievedContext: List<Map<String, Any?>>? = null
    private var outcome: String? = null
    private var rawOutput: String? = null
    private var verdict: Map<String, Any?>? = null
    private var errorDetail: String? = null
    private var latencyMs: Long? = null
    private var costMicros: Long? = null
    private var costCurrency: String? = null

    fun withEntryId(entryId: UUID?) = apply { this.entryId = entryId?.toString() }
    fun withDecisionId(decisionId: UUID?) = apply { this.decisionId = decisionId?.toString() }
    fun withCorrelationType(correlationType: String?) = apply { this.correlationType = correlationType }
    fun withCorrelationId(correlationId: String?) = apply { this.correlationId = correlationId }
    fun withAdvisory(advisory: Boolean?) = apply { this.advisory = advisory }
    fun withModelId(modelId: String?) = apply { this.modelId = modelId }
    fun withModelVersion(modelVersion: String?) = apply { this.modelVersion = modelVersion }
    fun withPromptTemplateId(promptTemplateId: String?) = apply { this.promptTemplateId = promptTemplateId }
    fun withPromptTemplateVersion(v: String?) = apply { this.promptTemplateVersion = v }
    fun withPromptTemplateHash(hash: String?) = apply { this.promptTemplateHash = hash }
    fun withRenderedPrompt(renderedPrompt: String?) = apply { this.renderedPrompt = renderedPrompt }
    fun withRetrievedContext(refs: List<Map<String, Any?>>?) = apply { this.retrievedContext = refs }
    fun withOutcome(outcome: String?) = apply { this.outcome = outcome }
    fun withRawOutput(rawOutput: String?) = apply { this.rawOutput = rawOutput }
    fun withVerdict(verdict: Map<String, Any?>?) = apply { this.verdict = verdict }
    fun withErrorDetail(errorDetail: String?) = apply { this.errorDetail = errorDetail }
    fun withLatencyMs(latencyMs: Long?) = apply { this.latencyMs = latencyMs }
    fun withCostMicros(costMicros: Long?) = apply { this.costMicros = costMicros }
    fun withCostCurrency(costCurrency: String?) = apply { this.costCurrency = costCurrency }

    override fun build(): String = JsonSerialiser.toJson(
        RecordModelDecisionRequestJson(
            entryId, decisionId, correlationType, correlationId, advisory, modelId, modelVersion,
            promptTemplateId, promptTemplateVersion, promptTemplateHash, renderedPrompt,
            retrievedContext, outcome, rawOutput, verdict, errorDetail, latencyMs, costMicros, costCurrency,
        ),
    )

    data class RecordModelDecisionRequestJson(
        val entryId: String?,
        val decisionId: String?,
        val correlationType: String?,
        val correlationId: String?,
        @get:JsonInclude(NON_NULL) val advisory: Boolean?,
        val modelId: String?,
        val modelVersion: String?,
        val promptTemplateId: String?,
        val promptTemplateVersion: String?,
        val promptTemplateHash: String?,
        val renderedPrompt: String?,
        val retrievedContext: List<Map<String, Any?>>?,
        val outcome: String?,
        val rawOutput: String?,
        val verdict: Map<String, Any?>?,
        val errorDetail: String?,
        val latencyMs: Long?,
        val costMicros: Long?,
        val costCurrency: String?,
    )

    companion object {
        fun aDefaultRecordModelDecisionRequest() = RecordModelDecisionRequestBuilder()
            .withEntryId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .withDecisionId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .withCorrelationType("PAYMENT")
            .withCorrelationId("pmt-abc-123")
            .withModelId("claude-opus-4-8")
            .withModelVersion("2026-01")
            .withPromptTemplateId("fraud-triage")
            .withPromptTemplateVersion("v3")
            .withPromptTemplateHash("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
            .withRenderedPrompt("Assess payment pmt-abc-123 for fraud risk.")
            .withRetrievedContext(listOf(mapOf("type" to "CASE", "id" to "case-42", "contentHash" to "abc123")))
            .withOutcome("COMPLETED")
            .withRawOutput("APPROVE: low risk.")
            .withVerdict(mapOf("decision" to "APPROVE", "rationale" to "no anomalies"))
            .withLatencyMs(842L)
            .withCostMicros(1500L)
            .withCostCurrency("GBP")

        fun aTimedOutRecordModelDecisionRequest() = aDefaultRecordModelDecisionRequest()
            .withOutcome("TIMED_OUT")
            .withRawOutput(null)
            .withVerdict(null)

        fun anErroredRecordModelDecisionRequest() = aDefaultRecordModelDecisionRequest()
            .withOutcome("ERRORED")
            .withRawOutput(null)
            .withVerdict(null)
            .withErrorDetail("Downstream returned HTTP 503 after 3 retries")
    }
}
