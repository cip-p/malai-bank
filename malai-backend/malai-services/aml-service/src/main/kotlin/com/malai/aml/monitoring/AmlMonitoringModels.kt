package com.malai.aml.monitoring

import java.time.Instant
import java.util.UUID

const val OUTBOUND = "OUTBOUND"
const val INBOUND = "INBOUND"

enum class AmlTypology {
    STRUCTURING,
    RAPID_IN_OUT,
    HIGH_RISK_CORRIDOR,
}

data class MonitoredPayment(
    val paymentId: UUID,
    val accountId: UUID,
    val direction: String,
    val rail: String,
    val amountMinorUnits: Long,
    val currency: String,
    val createdAt: Instant,
    val beneficiaryName: String?,
    val beneficiarySortCode: String?,
    val beneficiaryAccountNo: String?,
    val beneficiaryIban: String?,
    val beneficiaryBic: String?,
) {
    val contentHash: String = sha256Hex(
        listOf(
            paymentId.toString(),
            rail,
            direction,
            amountMinorUnits.toString(),
            currency,
            createdAt.toString(),
            beneficiaryName.orEmpty(),
            beneficiarySortCode.orEmpty(),
            beneficiaryAccountNo.orEmpty(),
            beneficiaryIban.orEmpty(),
            beneficiaryBic.orEmpty(),
        ).joinToString("|"),
    )

    val beneficiaryKey: String? = when {
        beneficiarySortCode != null && beneficiaryAccountNo != null ->
            "SC:${beneficiarySortCode.filter(Char::isDigit)}:${beneficiaryAccountNo.trim()}"
        beneficiaryIban != null ->
            "IBAN:${beneficiaryIban.filterNot(Char::isWhitespace).uppercase()}"
        else -> null
    }

    val beneficiaryJurisdiction: String? =
        beneficiaryIban?.filterNot(Char::isWhitespace)?.uppercase()?.take(2)?.takeIf { it.length == 2 }
            ?: beneficiaryBic?.trim()?.uppercase()?.takeIf { it.length >= 6 }?.substring(4, 6)
}

data class AmlWindow(
    val accountId: UUID,
    val from: Instant,
    val until: Instant,
    val payments: List<MonitoredPayment>,
)

data class TypologyHit(
    val typology: AmlTypology,
    val riskScore: Int,
    val evidence: List<MonitoredPayment>,
    val detail: Map<String, Any> = emptyMap(),
    val contextPins: List<ContextPin> = emptyList(),
)

data class ContextPin(
    val type: String,
    val id: String,
    val contentHash: String,
)
