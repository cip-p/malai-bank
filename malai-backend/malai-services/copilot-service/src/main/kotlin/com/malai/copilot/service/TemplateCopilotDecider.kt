package com.malai.copilot.service

import com.malai.copilot.model.SourceRecord
import com.malai.copilot.model.SourceRecordType
import com.malai.copilot.model.SourceRef
import com.malai.copilot.model.Timeline

class TemplateCopilotDecider(
    private val properties: CopilotProperties,
) : CopilotDecider {

    override val modelId: String = "malai-copilot-template"

    override val modelVersion: String = "v1"

    override val costCurrency: String = "GBP"

    override fun explain(timeline: Timeline, renderedPrompt: String): DeciderOutcome = DeciderOutcome(
        claims = when (timeline.subjectAmountMinorUnits) {
            properties.armedTimeoutAmountMinorUnits -> overrunTheBudget()
            properties.armedErrorAmountMinorUnits ->
                throw IllegalStateException("the armed copilot decider could not reach a model")
            properties.armedUngroundedAmountMinorUnits -> timeline.narrateRecords() + aFabricatedClaim()
            else -> timeline.narrateRecords()
        },
        modelVersion = modelVersion,
        costMicros = 0L,
    )

    private fun Timeline.narrateRecords(): List<Claim> =
        records.map { Claim(text = it.narrate(), sources = listOf(it.ref)) }

    private fun overrunTheBudget(): Nothing {
        Thread.sleep(properties.deciderTimeoutMs * ARMED_OVERRUN_FACTOR)
        throw IllegalStateException("the armed timeout subject outlived the budget that should have abandoned it")
    }

    private fun aFabricatedClaim() = Claim(
        text = "The bank posted a further ledger entry against this payment.",
        sources = listOf(SourceRef(SourceRecordType.LEDGER_ENTRY.name, FABRICATED_LEDGER_ENTRY_ID)),
    )

    private fun SourceRecord.narrate(): String = when (type) {
        SourceRecordType.PAYMENT -> "The payment was recorded at $moment with $summary."
        SourceRecordType.HOLD -> "At $moment the ledger reserved funds against this payment: $summary."
        SourceRecordType.SCREENING_DECISION -> "Screening reached a decision on this payment: $summary."
        SourceRecordType.AUDIT_EVENT -> "At $moment the bank recorded $summary."
        SourceRecordType.LEDGER_ENTRY -> "At $moment the ledger posted $summary."
    }

    private companion object {
        const val FABRICATED_LEDGER_ENTRY_ID = "00000000-0000-4000-8000-00000000dead"
        const val ARMED_OVERRUN_FACTOR = 2L
    }
}
