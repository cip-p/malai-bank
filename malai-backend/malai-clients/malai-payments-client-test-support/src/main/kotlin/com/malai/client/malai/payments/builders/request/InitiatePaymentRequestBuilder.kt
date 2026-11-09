package com.malai.client.malai.payments.builders.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import java.util.UUID

class InitiatePaymentRequestBuilder private constructor() : TestBuilder {

    private var fromAccountId: UUID? = null
    private var toSortCode: String? = null
    private var toAccountNumber: String? = null
    private var toIban: String? = null
    private var toBic: String? = null
    private var paymentPurpose: String? = null
    private var toName: String? = null
    private var amountMinorUnits: Long? = null
    private var currency: String? = null
    private var description: String? = null
    private var copVerdictOverride: Boolean = false

    fun withFromAccountId(fromAccountId: UUID?) = apply { this.fromAccountId = fromAccountId }
    fun withToSortCode(toSortCode: String?) = apply { this.toSortCode = toSortCode }
    fun withToAccountNumber(toAccountNumber: String?) = apply { this.toAccountNumber = toAccountNumber }
    fun withToIban(toIban: String?) = apply { this.toIban = toIban }
    fun withToBic(toBic: String?) = apply { this.toBic = toBic }
    fun withPaymentPurpose(paymentPurpose: String?) = apply { this.paymentPurpose = paymentPurpose }
    fun withToName(toName: String?) = apply { this.toName = toName }
    fun withAmountMinorUnits(amountMinorUnits: Long?) = apply { this.amountMinorUnits = amountMinorUnits }
    fun withCurrency(currency: String?) = apply { this.currency = currency }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withCopVerdictOverride(copVerdictOverride: Boolean) = apply { this.copVerdictOverride = copVerdictOverride }

    override fun build(): String = toJson(
        Payload(
            fromAccountId = fromAccountId?.toString(),
            toSortCode = toSortCode,
            toAccountNumber = toAccountNumber,
            toIban = toIban,
            toBic = toBic,
            paymentPurpose = paymentPurpose,
            toName = toName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
            copVerdictOverride = copVerdictOverride,
        )
    )

    @JsonInclude(NON_NULL)
    private data class Payload(
        val fromAccountId: String?,
        val toSortCode: String?,
        val toAccountNumber: String?,
        val toIban: String?,
        val toBic: String?,
        val paymentPurpose: String?,
        val toName: String?,
        val amountMinorUnits: Long?,
        val currency: String?,
        val description: String?,
        val copVerdictOverride: Boolean,
    )

    companion object {
        fun anInitiatePaymentRequest() = InitiatePaymentRequestBuilder()
    }
}
