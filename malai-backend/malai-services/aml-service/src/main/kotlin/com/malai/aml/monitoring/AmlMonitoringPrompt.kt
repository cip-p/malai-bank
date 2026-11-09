package com.malai.aml.monitoring

import org.springframework.stereotype.Component

@Component
class AmlMonitoringPrompt(
    private val properties: AmlProperties,
    private val jurisdictionList: HighRiskJurisdictionList,
) {

    private val template: String = javaClass.getResourceAsStream(TEMPLATE_RESOURCE)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("prompt template $TEMPLATE_RESOURCE is missing from the classpath")

    val templateId: String = "aml-monitoring"
    val templateVersion: String = "v1"
    val templateHash: String = sha256Hex(template)

    fun render(window: AmlWindow): String = template
        .replace("{{minPayments}}", properties.structuring.minPayments.toString())
        .replace("{{thresholdMinorUnits}}", properties.structuring.thresholdMinorUnits.toString())
        .replace("{{rapidInOutWindow}}", properties.rapidInOut.window.toString())
        .replace("{{minOutboundPercent}}", properties.rapidInOut.minOutboundPercent.toString())
        .replace("{{jurisdictionListVersion}}", jurisdictionList.version)
        .replace("{{jurisdictionListEntries}}", jurisdictionList.entries)
        .replace("{{accountId}}", window.accountId.toString())
        .replace("{{from}}", window.from.toString())
        .replace("{{until}}", window.until.toString())
        .replace("{{payments}}", window.payments.joinToString("\n") { paymentLine(it) })

    private fun paymentLine(payment: MonitoredPayment): String =
        "  - ${payment.paymentId} | ${payment.direction} ${payment.rail} | " +
            "${payment.amountMinorUnits} minor units of ${payment.currency} | at ${payment.createdAt} | " +
            "to ${payment.beneficiaryName ?: NOT_SUPPLIED} ${beneficiaryIdentifier(payment)}"

    private fun beneficiaryIdentifier(payment: MonitoredPayment): String = when {
        payment.beneficiarySortCode != null && payment.beneficiaryAccountNo != null ->
            "${payment.beneficiarySortCode} ${maskIdentifier(payment.beneficiaryAccountNo)}"
        payment.beneficiaryIban != null ->
            listOfNotNull(payment.beneficiaryBic, maskIdentifier(payment.beneficiaryIban)).joinToString(" ")
        else -> NOT_SUPPLIED
    }

    companion object {
        private const val TEMPLATE_RESOURCE = "/aml/aml-monitoring-v1.txt"
        private const val NOT_SUPPLIED = "not supplied"
    }
}
