package com.malai.payments.model

import java.time.Instant
import java.util.UUID

/**
 * The payment row as it stands, for an internal caller reconstructing why something happened.
 *
 * Deliberately not [PaymentResponse]: that shape is customer-facing and hides the debtor identity
 * on an outbound payment, omits the screening decision id, and exists to answer "show me my
 * payment". This one answers "what does the bank record about this payment", so it mirrors the row
 * whole — including the fields a customer never sees. Redaction is the caller's job, not the
 * record's.
 *
 * [createdAt] is the only moment the row carries; a payment's later moments — referred, released,
 * declined, returned — live on the audit plane against `resourceType = "Payment"` and are read
 * from there, not from here.
 */
data class InternalPaymentResponse(
    val paymentId: UUID,
    val state: String,
    val rail: String,
    val direction: String,
    val amountMinorUnits: Long,
    val currency: String,
    val endToEndId: UUID,
    val createdAt: Instant,
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
