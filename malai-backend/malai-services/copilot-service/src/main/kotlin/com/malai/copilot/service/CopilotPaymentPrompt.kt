package com.malai.copilot.service

import com.malai.copilot.model.Timeline
import com.malai.copilot.model.TimelineBalances
import com.malai.copilot.model.sha256Hex
import org.springframework.stereotype.Component

@Component
class CopilotPaymentPrompt {

    private val template: String = javaClass.getResourceAsStream(TEMPLATE_RESOURCE)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("prompt template $TEMPLATE_RESOURCE is missing from the classpath")

    val templateId: String = "copilot-payment"

    val templateVersion: String = "v2"

    val templateHash: String = sha256Hex(template)

    fun render(timeline: Timeline): String = template
        .replace("{{subjectType}}", timeline.subjectType)
        .replace("{{subjectId}}", timeline.subjectId.toString())
        .replace("{{balances}}", timeline.balances.render())
        .replace("{{timeline}}", timeline.records.joinToString("\n") {
            "${it.type.name} ${it.id} at ${it.moment} :: ${it.summary}"
        })

    private fun TimelineBalances?.render(): String = this
        ?.let {
            "account=${it.accountId} ledgerBalanceMinorUnits=${it.ledgerBalanceMinorUnits} " +
                "heldAmountMinorUnits=${it.heldAmountMinorUnits} " +
                "availableBalanceMinorUnits=${it.availableBalanceMinorUnits}"
        }
        ?: "not available"

    companion object {
        private const val TEMPLATE_RESOURCE = "/copilot/copilot-payment-v2.txt"
    }
}
