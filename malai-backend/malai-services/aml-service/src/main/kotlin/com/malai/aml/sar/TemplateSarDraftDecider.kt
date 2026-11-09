package com.malai.aml.sar

import com.malai.aml.monitoring.AmlProperties

class TemplateSarDraftDecider(
    private val properties: AmlProperties,
) : SarDraftDecider {

    override val modelId: String = "malai-sar-template"

    override val modelVersion: String = "1"

    override val costCurrency: String = "GBP"

    override fun draft(timeline: SarTimeline, renderedPrompt: String): SarDraftOutcome = SarDraftOutcome(
        statements = when {
            timeline.carries(properties.sar.armedTimeoutAmountMinorUnits) -> overrunTheBudget()
            timeline.carries(properties.sar.armedErrorAmountMinorUnits) ->
                throw IllegalStateException("the armed sar drafter could not reach a model")
            timeline.carries(properties.sar.armedUngroundedAmountMinorUnits) ->
                timeline.narrate() + aFabricatedStatement()
            else -> timeline.narrate()
        },
        modelVersion = modelVersion,
        costMicros = 0L,
    )

    private fun SarTimeline.carries(amountMinorUnits: Long): Boolean =
        amountMinorUnits in evidenceAmountsMinorUnits

    private fun SarTimeline.narrate(): List<SarStatement> = listOf(theOpeningStatement()) +
        records.map { SarStatement(text = it.narrate(), sources = listOf(it.ref)) }

    private fun SarTimeline.theOpeningStatement() = SarStatement(
        text = "Malai Bank reports activity it considers suspicious on account $accountId, " +
            "investigated as case $caseId opened at $openedAt.",
        sources = records.map { it.ref },
    )

    private fun SarRecord.narrate(): String = when (type) {
        SarRecordType.ALERT_DECISION ->
            "Transaction monitoring raised an alert on this account at $moment: $summary."
        SarRecordType.PAYMENT ->
            "The account settled a payment at $moment: $summary."
        SarRecordType.AUDIT_EVENT ->
            "At $moment the bank recorded $summary against this case."
    }

    private fun overrunTheBudget(): Nothing {
        Thread.sleep(properties.sar.deciderTimeoutMs * ARMED_OVERRUN_FACTOR)
        throw IllegalStateException("the armed timeout case outlived the budget that should have abandoned it")
    }

    private fun aFabricatedStatement() = SarStatement(
        text = "The account also sent a further payment the bank never retrieved.",
        sources = listOf(SarRef(SarRecordType.PAYMENT.name, FABRICATED_PAYMENT_ID)),
    )

    private companion object {
        const val FABRICATED_PAYMENT_ID = "00000000-0000-4000-8000-00000000dead"
        const val ARMED_OVERRUN_FACTOR = 2L
    }
}
