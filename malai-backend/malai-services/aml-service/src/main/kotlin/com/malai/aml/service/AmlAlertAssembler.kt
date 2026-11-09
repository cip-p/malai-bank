package com.malai.aml.service

import com.malai.aml.model.AmlAlertView
import com.malai.aml.model.AmlEvidencePayment
import com.malai.aml.model.AmlRuling
import com.malai.aml.model.AmlSarView
import com.malai.aml.monitoring.AmlTypology
import com.malai.client.malai.mdr.MdrClient
import com.malai.client.malai.mdr.model.DecisionRecordResponse
import com.malai.client.malai.payments.MalaiPaymentsClient
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AmlAlertAssembler(
    private val mdrClient: MdrClient,
    private val paymentsClient: MalaiPaymentsClient,
    private val serviceTokenClient: AmlServiceTokenClient,
) {

    fun assembleAlerts(alertDecisionIds: List<UUID>): List<AmlAlertView> {
        if (alertDecisionIds.isEmpty()) return emptyList()
        val serviceToken = serviceTokenClient.getToken()
        return alertDecisionIds
            .map { mdrClient.decisionRecord(serviceToken, it) }
            .map { it.toAlertView(serviceToken) }
            .sortedBy { it.raisedAt }
    }

    fun assembleSar(sarDecisionId: UUID, narrative: String?): AmlSarView {
        val record = mdrClient.decisionRecord(serviceTokenClient.getToken(), sarDecisionId)
        return AmlSarView(
            decisionId = sarDecisionId,
            rulingInForce = record.rulingInForce?.let { AmlRuling.valueOf(it) },
            narrative = narrative,
        )
    }

    private fun DecisionRecordResponse.toAlertView(serviceToken: String) = AmlAlertView(
        decisionId = decisionId,
        typology = AmlTypology.valueOf(verdict?.get(TYPOLOGY) as String),
        riskScore = (verdict?.get(RISK_SCORE) as Number).toInt(),
        raisedAt = Instant.parse(ts),
        rulingInForce = rulingInForce?.let { AmlRuling.valueOf(it) },
        evidence = evidenceOf(serviceToken),
    )

    private fun DecisionRecordResponse.evidenceOf(serviceToken: String): List<AmlEvidencePayment> =
        retrievedContext
            .orEmpty()
            .filter { it.type == PAYMENT_CONTEXT_TYPE }
            .map { ref ->
                val payment = paymentsClient.getPayment(serviceToken, UUID.fromString(ref.id))
                AmlEvidencePayment(
                    paymentId = payment.paymentId,
                    contentHash = ref.contentHash,
                    rail = payment.rail,
                    direction = payment.direction,
                    amountMinorUnits = payment.amountMinorUnits,
                    currency = payment.currency,
                    createdAt = Instant.parse(payment.createdAt),
                    beneficiaryName = payment.beneficiaryName,
                    beneficiarySortCode = payment.beneficiarySortCode,
                    beneficiaryAccountNo = payment.beneficiaryAccountNo,
                    beneficiaryIban = payment.beneficiaryIban,
                    beneficiaryBic = payment.beneficiaryBic,
                )
            }
            .sortedBy { it.createdAt }

    companion object {
        private const val PAYMENT_CONTEXT_TYPE = "PAYMENT"
        private const val TYPOLOGY = "typology"
        private const val RISK_SCORE = "riskScore"
    }
}
