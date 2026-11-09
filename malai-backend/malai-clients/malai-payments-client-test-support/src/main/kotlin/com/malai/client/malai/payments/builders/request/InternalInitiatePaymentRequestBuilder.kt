package com.malai.client.malai.payments.builders.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import java.util.UUID

class InternalInitiatePaymentRequestBuilder private constructor() : TestBuilder {

    private var endToEndId: UUID? = null
    private var fromAccountId: UUID? = null
    private var accountHolderId: UUID? = null
    private var toSortCode: String? = null
    private var toAccountNumber: String? = null
    private var toName: String? = null
    private var amountMinorUnits: Long? = null
    private var currency: String? = null
    private var reference: String? = null

    fun withEndToEndId(endToEndId: UUID?) = apply { this.endToEndId = endToEndId }
    fun withFromAccountId(fromAccountId: UUID?) = apply { this.fromAccountId = fromAccountId }
    fun withAccountHolderId(accountHolderId: UUID?) = apply { this.accountHolderId = accountHolderId }
    fun withToSortCode(toSortCode: String?) = apply { this.toSortCode = toSortCode }
    fun withToAccountNumber(toAccountNumber: String?) = apply { this.toAccountNumber = toAccountNumber }
    fun withToName(toName: String?) = apply { this.toName = toName }
    fun withAmountMinorUnits(amountMinorUnits: Long?) = apply { this.amountMinorUnits = amountMinorUnits }
    fun withCurrency(currency: String?) = apply { this.currency = currency }
    fun withReference(reference: String?) = apply { this.reference = reference }

    override fun build(): String = toJson(
        Payload(
            endToEndId = endToEndId?.toString(),
            fromAccountId = fromAccountId?.toString(),
            accountHolderId = accountHolderId?.toString(),
            toSortCode = toSortCode,
            toAccountNumber = toAccountNumber,
            toName = toName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            reference = reference,
        )
    )

    @JsonInclude(NON_NULL)
    private data class Payload(
        val endToEndId: String?,
        val fromAccountId: String?,
        val accountHolderId: String?,
        val toSortCode: String?,
        val toAccountNumber: String?,
        val toName: String?,
        val amountMinorUnits: Long?,
        val currency: String?,
        val reference: String?,
    )

    companion object {
        fun anInternalInitiatePaymentRequest() = InternalInitiatePaymentRequestBuilder()
    }
}
