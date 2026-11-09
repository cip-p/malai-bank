package com.malai.client.malai.payments.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID
import kotlin.properties.Delegates

class SettledPaymentsResponseBuilder private constructor() : TestBuilder {

    private var payments: List<SettledPaymentBuilder> = emptyList()

    fun withPayments(vararg payments: SettledPaymentBuilder) = apply { this.payments = payments.toList() }

    override fun build(): String = JsonSerialiser.toJson(Payload(payments.map { it.build() }))

    data class Payload(val payments: List<SettledPaymentBuilder.Payment>)

    companion object {
        fun aSettledPaymentsResponse() = SettledPaymentsResponseBuilder()
    }

    class SettledPaymentBuilder private constructor() {

        private lateinit var paymentId: UUID
        private lateinit var state: String
        private lateinit var rail: String
        private lateinit var direction: String
        private var amountMinorUnits by Delegates.notNull<Long>()
        private lateinit var currency: String
        private lateinit var createdAt: String
        private lateinit var endToEndId: UUID
        private var debtorAccountId: UUID? = null
        private var debtorSortCode: String? = null
        private var debtorAccountNo: String? = null
        private var debtorName: String? = null
        private var debtorIban: String? = null
        private var debtorBic: String? = null
        private var beneficiaryAccountId: UUID? = null
        private var beneficiarySortCode: String? = null
        private var beneficiaryAccountNo: String? = null
        private var beneficiaryName: String? = null
        private var beneficiaryIban: String? = null
        private var beneficiaryBic: String? = null

        fun withPaymentId(paymentId: UUID) = apply { this.paymentId = paymentId }
        fun withState(state: String) = apply { this.state = state }
        fun withRail(rail: String) = apply { this.rail = rail }
        fun withDirection(direction: String) = apply { this.direction = direction }
        fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }
        fun withCurrency(currency: String) = apply { this.currency = currency }
        fun withCreatedAt(createdAt: String) = apply { this.createdAt = createdAt }
        fun withEndToEndId(endToEndId: UUID) = apply { this.endToEndId = endToEndId }
        fun withDebtorAccountId(debtorAccountId: UUID?) = apply { this.debtorAccountId = debtorAccountId }
        fun withDebtorSortCode(debtorSortCode: String?) = apply { this.debtorSortCode = debtorSortCode }
        fun withDebtorAccountNo(debtorAccountNo: String?) = apply { this.debtorAccountNo = debtorAccountNo }
        fun withDebtorName(debtorName: String?) = apply { this.debtorName = debtorName }
        fun withDebtorIban(debtorIban: String?) = apply { this.debtorIban = debtorIban }
        fun withDebtorBic(debtorBic: String?) = apply { this.debtorBic = debtorBic }
        fun withBeneficiaryAccountId(beneficiaryAccountId: UUID?) =
            apply { this.beneficiaryAccountId = beneficiaryAccountId }
        fun withBeneficiarySortCode(beneficiarySortCode: String?) =
            apply { this.beneficiarySortCode = beneficiarySortCode }
        fun withBeneficiaryAccountNo(beneficiaryAccountNo: String?) =
            apply { this.beneficiaryAccountNo = beneficiaryAccountNo }
        fun withBeneficiaryName(beneficiaryName: String?) = apply { this.beneficiaryName = beneficiaryName }
        fun withBeneficiaryIban(beneficiaryIban: String?) = apply { this.beneficiaryIban = beneficiaryIban }
        fun withBeneficiaryBic(beneficiaryBic: String?) = apply { this.beneficiaryBic = beneficiaryBic }

        fun build() = Payment(
            paymentId, state, rail, direction, amountMinorUnits, currency, createdAt, endToEndId,
            debtorAccountId, debtorSortCode, debtorAccountNo, debtorName, debtorIban, debtorBic,
            beneficiaryAccountId, beneficiarySortCode, beneficiaryAccountNo, beneficiaryName,
            beneficiaryIban, beneficiaryBic,
        )

        data class Payment(
            val paymentId: UUID,
            val state: String,
            val rail: String,
            val direction: String,
            val amountMinorUnits: Long,
            val currency: String,
            val createdAt: String,
            val endToEndId: UUID,
            val debtorAccountId: UUID?,
            val debtorSortCode: String?,
            val debtorAccountNo: String?,
            val debtorName: String?,
            val debtorIban: String?,
            val debtorBic: String?,
            val beneficiaryAccountId: UUID?,
            val beneficiarySortCode: String?,
            val beneficiaryAccountNo: String?,
            val beneficiaryName: String?,
            val beneficiaryIban: String?,
            val beneficiaryBic: String?,
        )

        companion object {
            fun aSettledPayment() = SettledPaymentBuilder()
                .withPaymentId(UUID.randomUUID())
                .withState("SETTLED")
                .withRail("FPS")
                .withDirection("OUTBOUND")
                .withAmountMinorUnits(2_500L)
                .withCurrency("GBP")
                .withCreatedAt("2026-10-15T10:30:00Z")
                .withEndToEndId(UUID.randomUUID())
        }
    }
}
