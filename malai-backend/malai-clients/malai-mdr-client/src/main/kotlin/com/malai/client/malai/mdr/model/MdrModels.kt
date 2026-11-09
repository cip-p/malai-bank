package com.malai.client.malai.mdr.model

import java.util.UUID

enum class ModelDecisionOutcome { COMPLETED, TIMED_OUT, ERRORED }

data class RetrievedContextRef(
    val type: String,
    val id: String,
    val contentHash: String,
)

data class RecordModelDecisionRequest(
    val entryId: UUID,
    val decisionId: UUID,
    val correlationType: String,
    val correlationId: String,
    val advisory: Boolean = false,
    val modelId: String,
    val modelVersion: String,
    val promptTemplateId: String,
    val promptTemplateVersion: String,
    val promptTemplateHash: String,
    val renderedPrompt: String,
    val retrievedContext: List<RetrievedContextRef>? = null,
    val outcome: ModelDecisionOutcome,
    val rawOutput: String? = null,
    val verdict: Map<String, Any?>? = null,
    val errorDetail: String? = null,
    val latencyMs: Long,
    val costMicros: Long,
    val costCurrency: String,
)

data class RecordModelDecisionResponse(
    val entryId: UUID,
    val decisionId: UUID,
    val seq: Long,
    val outcome: String,
)

data class DecisionRecordResponse(
    val decisionId: UUID,
    val entryId: UUID,
    val seq: Long,
    val ts: String,
    val outcome: String,
    val advisory: Boolean,
    val correlationType: String,
    val correlationId: String,
    val modelId: String,
    val modelVersion: String,
    val verdict: Map<String, Any?>? = null,
    val retrievedContext: List<RetrievedContextRef>? = null,
    val errorDetail: String? = null,
    val rulingInForce: String? = null,
)

data class ActionableOutputResponse(
    val decisionId: UUID,
    val ruling: String,
    val rulingEntryId: UUID,
    val actionableOutput: String,
)
