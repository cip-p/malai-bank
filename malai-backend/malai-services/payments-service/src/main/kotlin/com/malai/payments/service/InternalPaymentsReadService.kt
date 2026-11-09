package com.malai.payments.service

import com.malai.payments.entity.PaymentEntity
import com.malai.payments.exception.PaymentNotFoundException
import com.malai.payments.model.InternalPaymentResponse
import com.malai.payments.model.SettledPayment
import com.malai.payments.model.SettledPaymentsResponse
import com.malai.payments.repository.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class InternalPaymentsReadService(
    private val paymentRepository: PaymentRepository,
) {

    @Transactional(readOnly = true)
    fun getPayment(paymentId: UUID): InternalPaymentResponse =
        paymentRepository.findById(paymentId)
            .orElseThrow { PaymentNotFoundException() }
            .toInternalResponse()

    @Transactional(readOnly = true)
    fun settledPaymentsIn(from: Instant, until: Instant): SettledPaymentsResponse =
        SettledPaymentsResponse(
            paymentRepository.findSettledInWindow(from, until).map { it.toSettledPayment() },
        )

    private fun PaymentEntity.toSettledPayment() = SettledPayment(
        paymentId = paymentId,
        state = state.name,
        rail = rail.name,
        direction = direction.name,
        amountMinorUnits = amountMinorUnits,
        currency = currency.name,
        createdAt = createdAt,
        endToEndId = endToEndId,
        debtorAccountId = debtorAccountId,
        debtorSortCode = debtorSortCode,
        debtorAccountNo = debtorAccountNo,
        debtorName = debtorName,
        debtorIban = debtorIban,
        debtorBic = debtorBic,
        beneficiaryAccountId = beneficiaryAccountId,
        beneficiarySortCode = beneficiarySortCode,
        beneficiaryAccountNo = beneficiaryAccountNo,
        beneficiaryName = beneficiaryName,
        beneficiaryIban = beneficiaryIban,
        beneficiaryBic = beneficiaryBic,
    )

    private fun PaymentEntity.toInternalResponse() = InternalPaymentResponse(
        paymentId = paymentId,
        state = state.name,
        rail = rail.name,
        direction = direction.name,
        amountMinorUnits = amountMinorUnits,
        currency = currency.name,
        endToEndId = endToEndId,
        createdAt = createdAt,
        copVerdictOverride = copVerdictOverride,
        description = description,
        paymentPurpose = paymentPurpose,
        screeningDecisionId = screeningDecisionId,
        returnReason = returnReason?.name,
        fxTargetCurrency = fxTargetCurrency?.name,
        createdByAccountHolderId = createdByAccountHolderId,
        traceId = traceId,
        debtorAccountId = debtorAccountId,
        debtorSortCode = debtorSortCode,
        debtorAccountNo = debtorAccountNo,
        debtorName = debtorName,
        debtorIban = debtorIban,
        debtorBic = debtorBic,
        beneficiaryAccountId = beneficiaryAccountId,
        beneficiarySortCode = beneficiarySortCode,
        beneficiaryAccountNo = beneficiaryAccountNo,
        beneficiaryName = beneficiaryName,
        beneficiaryIban = beneficiaryIban,
        beneficiaryBic = beneficiaryBic,
    )
}
