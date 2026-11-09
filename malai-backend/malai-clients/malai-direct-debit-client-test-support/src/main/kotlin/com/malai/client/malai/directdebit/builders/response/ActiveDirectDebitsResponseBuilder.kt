package com.malai.client.malai.directdebit.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class ActiveDirectDebitsResponseBuilder private constructor() : TestBuilder {

    private val directDebits = mutableListOf<ActiveDirectDebitBuilder>()

    fun withDirectDebits(vararg builders: ActiveDirectDebitBuilder) =
        apply { directDebits.addAll(builders) }

    override fun build(): String = JsonSerialiser.toJson(
        ActiveDirectDebits(directDebits.map { it.toModel() }),
    )

    data class ActiveDirectDebits(val directDebits: List<ActiveDirectDebit>)

    data class ActiveDirectDebit(
        val directDebitId: UUID,
        val accountId: UUID,
        val creditorName: String,
        val amountMinorUnits: Long?,
        val currency: String,
        val frequency: String,
        val status: String,
        val startDate: String,
    )

    class ActiveDirectDebitBuilder private constructor() {
        private var directDebitId: UUID = UUID.randomUUID()
        private var accountId: UUID = UUID.randomUUID()
        private var creditorName: String = "British Gas"
        private var amountMinorUnits: Long? = 4_500
        private var currency: String = "GBP"
        private var frequency: String = "MONTHLY"
        private var status: String = "ACTIVE"
        private var startDate: String = "2026-06-01"

        fun withDirectDebitId(value: UUID) = apply { this.directDebitId = value }
        fun withAccountId(value: UUID) = apply { this.accountId = value }
        fun withCreditorName(value: String) = apply { this.creditorName = value }
        fun withAmountMinorUnits(value: Long?) = apply { this.amountMinorUnits = value }
        fun withCurrency(value: String) = apply { this.currency = value }
        fun withFrequency(value: String) = apply { this.frequency = value }
        fun withStatus(value: String) = apply { this.status = value }
        fun withStartDate(value: String) = apply { this.startDate = value }

        fun toModel() = ActiveDirectDebit(
            directDebitId = directDebitId,
            accountId = accountId,
            creditorName = creditorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            frequency = frequency,
            status = status,
            startDate = startDate,
        )

        companion object {
            fun anActiveDirectDebit() = ActiveDirectDebitBuilder()
        }
    }

    companion object {
        fun anActiveDirectDebitsResponse() = ActiveDirectDebitsResponseBuilder()
    }
}
