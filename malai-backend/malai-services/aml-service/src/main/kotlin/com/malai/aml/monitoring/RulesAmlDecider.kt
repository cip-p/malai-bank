package com.malai.aml.monitoring

import org.springframework.stereotype.Component

@Component
class RulesAmlDecider(
    private val properties: AmlProperties,
    private val jurisdictionList: HighRiskJurisdictionList,
) : AmlMonitoringDecider {

    override val modelId: String = "malai-aml-rules"
    override val modelVersion: String = "1"

    override fun decide(window: AmlWindow, renderedPrompt: String): List<TypologyHit> {
        window.payments.firstOrNull { it.amountMinorUnits == properties.armedTimeoutAmountMinorUnits }?.let {
            throw AmlDeciderTimedOutException("aml decider timed out over payment ${it.paymentId}")
        }
        window.payments.firstOrNull { it.amountMinorUnits == properties.armedErrorAmountMinorUnits }?.let {
            error("aml decider errored over payment ${it.paymentId}")
        }
        return structuringHits(window) + rapidInOutHits(window) + corridorHits(window)
    }

    private fun rapidInOutHits(window: AmlWindow): List<TypologyHit> {
        val rapidInOut = properties.rapidInOut
        val windowed = windowedPayments(window)
        return windowed
            .filter { it.direction == INBOUND }
            .mapNotNull { inbound ->
                val drains = windowed
                    .filter { it.direction == OUTBOUND }
                    .filter { it.createdAt >= inbound.createdAt }
                    .filter { it.createdAt < inbound.createdAt.plus(rapidInOut.window) }
                    .sortedBy { it.createdAt }
                val drainedMinorUnits = drains.sumOf { it.amountMinorUnits }
                if (drains.isNotEmpty() &&
                    drainedMinorUnits * 100 >= inbound.amountMinorUnits * rapidInOut.minOutboundPercent
                ) {
                    TypologyHit(
                        typology = AmlTypology.RAPID_IN_OUT,
                        riskScore = rapidInOutRiskScore(drainedMinorUnits, inbound.amountMinorUnits),
                        evidence = listOf(inbound) + drains,
                        detail = mapOf(
                            "inboundPaymentId" to inbound.paymentId.toString(),
                            "drainedPercent" to (drainedMinorUnits * 100 / inbound.amountMinorUnits).toInt(),
                        ),
                    )
                } else {
                    null
                }
            }
    }

    private fun rapidInOutRiskScore(drainedMinorUnits: Long, inboundMinorUnits: Long): Int =
        if (drainedMinorUnits >= inboundMinorUnits) 90 else 75

    private fun windowedPayments(window: AmlWindow): List<MonitoredPayment> =
        window.payments.filter { it.createdAt >= window.from && it.createdAt < window.until }

    private fun corridorHits(window: AmlWindow): List<TypologyHit> =
        windowedPayments(window)
            .filter { it.direction == OUTBOUND && it.rail == SWIFT_RAIL }
            .filter { jurisdictionList.flags(it.beneficiaryJurisdiction) }
            .map { payment ->
                TypologyHit(
                    typology = AmlTypology.HIGH_RISK_CORRIDOR,
                    riskScore = CORRIDOR_RISK_SCORE,
                    evidence = listOf(payment),
                    detail = mapOf("jurisdiction" to payment.beneficiaryJurisdiction!!),
                    contextPins = listOf(
                        ContextPin(
                            type = JURISDICTION_LIST_CONTEXT_TYPE,
                            id = jurisdictionList.version,
                            contentHash = jurisdictionList.contentHash,
                        ),
                    ),
                )
            }

    private fun structuringHits(window: AmlWindow): List<TypologyHit> {
        val structuring = properties.structuring
        return windowedPayments(window)
            .filter { it.direction == OUTBOUND }
            .filter { it.amountMinorUnits < structuring.thresholdMinorUnits }
            .filter { it.beneficiaryKey != null }
            .groupBy { it.beneficiaryKey }
            .values
            .mapNotNull { group ->
                val jointMinorUnits = group.sumOf { it.amountMinorUnits }
                if (group.size >= structuring.minPayments && jointMinorUnits >= structuring.thresholdMinorUnits) {
                    TypologyHit(
                        typology = AmlTypology.STRUCTURING,
                        riskScore = structuringRiskScore(jointMinorUnits, structuring.thresholdMinorUnits),
                        evidence = group.sortedBy { it.createdAt },
                    )
                } else {
                    null
                }
            }
    }

    private fun structuringRiskScore(jointMinorUnits: Long, thresholdMinorUnits: Long): Int =
        when {
            jointMinorUnits >= thresholdMinorUnits * 5 -> 95
            jointMinorUnits >= thresholdMinorUnits * 2 -> 85
            else -> 70
        }

    companion object {
        private const val JURISDICTION_LIST_CONTEXT_TYPE = "JURISDICTION_LIST"
        private const val SWIFT_RAIL = "SWIFT"
        private const val CORRIDOR_RISK_SCORE = 80
    }
}
