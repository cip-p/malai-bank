package com.malai.payments.entity

import com.malai.commons.model.Currency
import com.malai.payments.messages.ReturnReason
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
data class PaymentEntity(

    @Id
    @Column(name = "payment_id", nullable = false)
    val paymentId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "rail", nullable = false, length = 20)
    val rail: Rail,

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    val direction: PaymentDirection,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 15)
    val state: PaymentState,

    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason", length = 50)
    val returnReason: ReturnReason? = null,

    @Column(name = "debtor_account_id")
    val debtorAccountId: UUID? = null,

    @Column(name = "debtor_sort_code", length = 8)
    val debtorSortCode: String? = null,

    @Column(name = "debtor_account_no", length = 8)
    val debtorAccountNo: String? = null,

    @Column(name = "debtor_name", length = 255)
    val debtorName: String? = null,

    @Column(name = "debtor_iban", length = 34)
    val debtorIban: String? = null,

    @Column(name = "debtor_bic", length = 11)
    val debtorBic: String? = null,

    @Column(name = "beneficiary_account_id")
    val beneficiaryAccountId: UUID? = null,

    @Column(name = "beneficiary_sort_code", length = 8)
    val beneficiarySortCode: String? = null,

    @Column(name = "beneficiary_account_no", length = 8)
    val beneficiaryAccountNo: String? = null,

    @Column(name = "beneficiary_name", length = 255)
    val beneficiaryName: String? = null,

    @Column(name = "beneficiary_iban", length = 34)
    val beneficiaryIban: String? = null,

    @Column(name = "beneficiary_bic", length = 11)
    val beneficiaryBic: String? = null,

    @Column(name = "payment_purpose", length = 255)
    val paymentPurpose: String? = null,

    @Column(name = "amount_minor_units", nullable = false)
    val amountMinorUnits: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    val currency: Currency,

    @Column(name = "description", length = 500)
    val description: String? = null,

    @Column(name = "end_to_end_id", nullable = false, unique = true)
    val endToEndId: UUID,

    @Column(name = "cop_verdict_override", nullable = false)
    val copVerdictOverride: Boolean = false,

    @Column(name = "screening_decision_id")
    val screeningDecisionId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "fx_target_currency", length = 3)
    val fxTargetCurrency: Currency? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "created_by_account_holder_id")
    val createdByAccountHolderId: UUID? = null,

    @Column(name = "trace_id", length = 64)
    val traceId: String? = null,
)

enum class Rail {
    BOOK_TRANSFER,
    FPS,
    SWIFT,
    BACS_DIRECT_CREDIT,
    BACS_DIRECT_DEBIT,
}

enum class PaymentDirection {
    OUTBOUND,
    INBOUND,
}

enum class PaymentState {
    PENDING,
    SETTLED,
    RETURNED,
    REFERRED,
    DECLINED,
}
