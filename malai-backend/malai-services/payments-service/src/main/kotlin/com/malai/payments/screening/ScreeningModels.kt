package com.malai.payments.screening

import com.malai.client.malai.accounts.lookup.model.InternalAccountResponse
import com.malai.commons.model.Currency
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.Rail
import java.util.UUID

data class PayerAccountSnapshot(
    val accountId: UUID,
    val name: String,
    val identifier: String,
    val currency: Currency,
) {
    val contentHash: String = sha256Hex("$accountId|$name|$identifier|${currency.name}")

    companion object {
        fun of(account: InternalAccountResponse) = PayerAccountSnapshot(
            accountId = account.accountId,
            name = account.name,
            identifier = "${account.sortCode} ${maskIdentifier(account.accountNumber)}",
            currency = Currency.valueOf(account.currency),
        )
    }
}

data class ScreeningSubject(
    val paymentId: UUID,
    val rail: Rail,
    val amountMinorUnits: Long,
    val currency: Currency,
    val beneficiaryName: String?,
    val beneficiaryIdentifier: String,
    val paymentPurpose: String?,
    val payerAccount: PayerAccountSnapshot,
) {
    companion object {
        fun of(payment: PaymentEntity, payerAccount: InternalAccountResponse) = ScreeningSubject(
            paymentId = payment.paymentId,
            rail = payment.rail,
            amountMinorUnits = payment.amountMinorUnits,
            currency = payment.currency,
            beneficiaryName = payment.beneficiaryName,
            beneficiaryIdentifier = beneficiaryIdentifierOf(payment),
            paymentPurpose = payment.paymentPurpose,
            payerAccount = PayerAccountSnapshot.of(payerAccount),
        )

        private fun beneficiaryIdentifierOf(payment: PaymentEntity): String = when (payment.rail) {
            Rail.SWIFT -> "${payment.beneficiaryBic.orEmpty()} ${maskIdentifier(payment.beneficiaryIban.orEmpty())}"
            else -> "${payment.beneficiarySortCode.orEmpty()} ${maskIdentifier(payment.beneficiaryAccountNo.orEmpty())}"
        }.trim()
    }
}

enum class ScreeningDecision { APPROVE, DECLINE }

data class ScreeningVerdict(
    val decision: ScreeningDecision,
    val riskScore: Int,
    val reasons: List<String>,
) {
    fun asRecordedVerdict(): Map<String, Any?> = mapOf(
        "decision" to decision.name,
        "riskScore" to riskScore,
        "reasons" to reasons,
    )
}

data class ScreeningResult(
    val decisionId: UUID,
    val verdict: ScreeningVerdict?,
) {
    val flagged: Boolean = verdict?.decision == ScreeningDecision.DECLINE
}
