package com.malai.client.malai.payments.model

import java.util.UUID

data class InternalPaymentResponse(
    val paymentId: UUID,
    val state: String,
    val rail: String,
    val direction: String,
    val amountMinorUnits: Long,
    val currency: String,
    val endToEndId: UUID,
    val createdAt: String,
    val copVerdictOverride: Boolean,
    val description: String? = null,
    val paymentPurpose: String? = null,
    val screeningDecisionId: UUID? = null,
    val returnReason: String? = null,
    val fxTargetCurrency: String? = null,
    val createdByAccountHolderId: UUID? = null,
    val traceId: String? = null,
    val debtorAccountId: UUID? = null,
    val debtorSortCode: String? = null,
    val debtorAccountNo: String? = null,
    val debtorName: String? = null,
    val debtorIban: String? = null,
    val debtorBic: String? = null,
    val beneficiaryAccountId: UUID? = null,
    val beneficiarySortCode: String? = null,
    val beneficiaryAccountNo: String? = null,
    val beneficiaryName: String? = null,
    val beneficiaryIban: String? = null,
    val beneficiaryBic: String? = null,
)
