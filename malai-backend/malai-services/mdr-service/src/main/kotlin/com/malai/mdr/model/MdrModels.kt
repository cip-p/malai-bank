package com.malai.mdr.model

import com.malai.commons.model.Currency
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

enum class ModelDecisionOutcome { COMPLETED, TIMED_OUT, ERRORED }

data class RecordModelDecisionRequest(
    @field:NotNull val entryId: UUID?,
    @field:NotNull val decisionId: UUID?,
    @field:NotBlank val correlationType: String?,
    @field:NotBlank val correlationId: String?,
    val advisory: Boolean = false,
    @field:NotBlank val modelId: String?,
    @field:NotBlank val modelVersion: String?,
    @field:NotBlank val promptTemplateId: String?,
    @field:NotBlank val promptTemplateVersion: String?,
    @field:NotBlank val promptTemplateHash: String?,
    @field:NotBlank val renderedPrompt: String?,
    @field:Valid val retrievedContext: List<RetrievedContextRef>? = null,
    @field:NotNull val outcome: ModelDecisionOutcome?,
    val rawOutput: String? = null,
    val verdict: Map<String, Any?>? = null,
    val errorDetail: String? = null,
    @field:NotNull val latencyMs: Long?,
    @field:NotNull val costMicros: Long?,
    @field:NotNull val costCurrency: Currency?,
) {
    @get:AssertTrue(message = "rawOutput must be present iff outcome is COMPLETED")
    val rawOutputMatchesOutcome: Boolean
        get() = when (outcome) {
            ModelDecisionOutcome.COMPLETED -> !rawOutput.isNullOrBlank()
            ModelDecisionOutcome.TIMED_OUT, ModelDecisionOutcome.ERRORED -> rawOutput == null
            null -> true
        }

    @get:AssertTrue(message = "errorDetail must be present iff outcome is ERRORED")
    val errorDetailMatchesOutcome: Boolean
        get() = when (outcome) {
            ModelDecisionOutcome.ERRORED -> !errorDetail.isNullOrBlank()
            ModelDecisionOutcome.COMPLETED, ModelDecisionOutcome.TIMED_OUT -> errorDetail == null
            null -> true
        }
}

data class RetrievedContextRef(
    @field:NotBlank val type: String?,
    @field:NotBlank val id: String?,
    @field:NotBlank val contentHash: String?,
)

data class RecordModelDecisionResponse(
    val entryId: UUID,
    val decisionId: UUID,
    val seq: Long,
    val outcome: String,
)
