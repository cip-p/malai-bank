package com.malai.aml.sar

interface SarDraftDecider {

    val modelId: String

    val modelVersion: String

    val costCurrency: String

    fun draft(timeline: SarTimeline, renderedPrompt: String): SarDraftOutcome
}

data class SarDraftOutcome(
    val statements: List<SarStatement>,
    val modelVersion: String,
    val costMicros: Long,
)

data class SarStatement(
    val text: String,
    val sources: List<SarRef>,
)

class SarDraftTimedOutException(message: String) : RuntimeException(message)
