package com.malai.aml.sar

import com.malai.aml.model.AmlAlertView
import com.malai.aml.model.AmlEvidencePayment
import com.malai.aml.monitoring.maskIdentifier
import com.malai.security.audit.AuditEventRecord
import java.time.Instant
import java.util.UUID

object SarTimelineComposer {

    const val CASE_RESOURCE_TYPE = "AmlCase"

    fun compose(
        caseId: UUID,
        accountId: UUID,
        openedAt: Instant,
        alerts: List<AmlAlertView>,
        auditEvents: List<AuditEventRecord>,
    ): SarTimeline = SarTimeline(
        caseId = caseId,
        accountId = accountId,
        openedAt = openedAt,
        records = buildList {
            alerts.forEach { alert ->
                add(alert.asRecord())
                alert.evidence.forEach { add(it.asRecord()) }
            }
            auditEvents.forEach { add(it.asRecord()) }
        }
            .distinctBy { it.type to it.id }
            .sortedWith(compareBy({ it.moment }, { it.type.timelineRank }, { it.id })),
        evidenceAmountsMinorUnits = alerts.flatMap { it.evidence }.map { it.amountMinorUnits },
    )

    private fun AmlAlertView.asRecord() = SarRecord(
        type = SarRecordType.ALERT_DECISION,
        id = decisionId.toString(),
        moment = raisedAt,
        summary = listOf(
            "typology=$typology",
            "riskScore=$riskScore",
            "rulingInForce=${rulingInForce?.name ?: NOT_RULED}",
            "evidenceCount=${evidence.size}",
        ).joinToString(" "),
    )

    private fun AmlEvidencePayment.asRecord() = SarRecord(
        type = SarRecordType.PAYMENT,
        id = paymentId.toString(),
        moment = createdAt,
        summary = listOf(
            "rail=$rail",
            "direction=$direction",
            "amountMinorUnits=$amountMinorUnits",
            "currency=$currency",
            "beneficiaryName=${beneficiaryName ?: NOT_SUPPLIED}",
            "beneficiary=${maskedBeneficiary()}",
        ).joinToString(" "),
        contentHash = contentHash,
    )

    private fun AmlEvidencePayment.maskedBeneficiary(): String = when {
        beneficiaryIban != null -> maskIdentifier(beneficiaryIban)
        beneficiaryAccountNo != null -> "${beneficiarySortCode.orEmpty()} ${maskIdentifier(beneficiaryAccountNo)}".trim()
        else -> NOT_SUPPLIED
    }

    private fun AuditEventRecord.asRecord() = SarRecord(
        type = SarRecordType.AUDIT_EVENT,
        id = eventId.toString(),
        moment = ts,
        summary = "action=$action actor=$actorType afterState=${afterState.orEmpty().toSortedMap()}",
    )

    private const val NOT_SUPPLIED = "not supplied"
    private const val NOT_RULED = "not ruled"
}
