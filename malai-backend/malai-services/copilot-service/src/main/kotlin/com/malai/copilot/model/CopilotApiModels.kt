package com.malai.copilot.model

import jakarta.validation.constraints.NotNull
import java.util.UUID

enum class SubjectType { PAYMENT }

data class ExplanationRequest(
    @field:NotNull val subjectType: SubjectType?,
    @field:NotNull val subjectId: UUID?,
)

data class ClaimResponse(
    val text: String,
    val sources: List<SourceRef>,
)

enum class RefusalCode { GROUNDING_FAILED }

data class ExplanationResponse(
    val decisionId: UUID,
    val outcome: String,
    val claims: List<ClaimResponse>,
    val refusalCode: RefusalCode? = null,
)
