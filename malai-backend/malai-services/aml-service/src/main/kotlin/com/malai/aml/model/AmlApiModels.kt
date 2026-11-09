package com.malai.aml.model

import com.malai.aml.monitoring.AmlTypology
import java.time.Instant
import java.util.UUID

enum class AmlCaseStatus {
    OPEN,
    INVESTIGATING,
    CLOSED_DISMISSED,
    CLOSED_REPORTED,
}

data class AmlCaseSummariesResponse(
    val cases: List<AmlCaseSummary>,
)

data class AmlCaseSummary(
    val caseId: UUID,
    val accountId: UUID,
    val status: AmlCaseStatus,
    val alertCount: Int,
    val openedAt: Instant,
    val updatedAt: Instant,
)

enum class AmlRuling {
    ACCEPT,
    EDIT,
    REJECT,
}

data class AmlCaseDetailResponse(
    val caseId: UUID,
    val accountId: UUID,
    val status: AmlCaseStatus,
    val openedAt: Instant,
    val updatedAt: Instant,
    val alerts: List<AmlAlertView>,
    val sar: AmlSarView? = null,
)

data class AmlAlertView(
    val decisionId: UUID,
    val typology: AmlTypology,
    val riskScore: Int,
    val raisedAt: Instant,
    val rulingInForce: AmlRuling? = null,
    val evidence: List<AmlEvidencePayment>,
)

data class AmlEvidencePayment(
    val paymentId: UUID,
    val contentHash: String,
    val rail: String,
    val direction: String,
    val amountMinorUnits: Long,
    val currency: String,
    val createdAt: Instant,
    val beneficiaryName: String? = null,
    val beneficiarySortCode: String? = null,
    val beneficiaryAccountNo: String? = null,
    val beneficiaryIban: String? = null,
    val beneficiaryBic: String? = null,
)

data class AmlSarView(
    val decisionId: UUID,
    val rulingInForce: AmlRuling? = null,
    val narrative: String? = null,
)

enum class SarRefusalCode {
    GROUNDING_FAILED,
}

data class AmlSarDraftResponse(
    val decisionId: UUID,
    val outcome: String,
    val narrative: String? = null,
    val refusalCode: SarRefusalCode? = null,
)
