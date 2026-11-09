package com.malai.copilot.service

import com.malai.copilot.model.SourceRef
import com.malai.copilot.model.Timeline

interface CopilotDecider {

    val modelId: String

    val modelVersion: String

    val costCurrency: String

    fun explain(timeline: Timeline, renderedPrompt: String): DeciderOutcome
}

data class DeciderOutcome(
    val claims: List<Claim>,
    val modelVersion: String,
    val costMicros: Long,
)

data class Claim(
    val text: String,
    val sources: List<SourceRef>,
)

class CopilotTimedOutException(message: String) : RuntimeException(message)
