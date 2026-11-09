package com.malai.client.malai.payments.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import com.malai.payments.messages.ReturnReason
import java.time.Instant
import java.util.UUID

class InitiatePaymentResponseBuilder private constructor() : TestBuilder {

    private lateinit var paymentId: UUID
    private lateinit var state: String
    private lateinit var rail: String
    private lateinit var direction: String
    private lateinit var createdAt: Instant
    private lateinit var endToEndId: UUID
    private var fromAccountId: UUID? = null
    private var fromSortCode: String? = null
    private var fromAccountNumber: String? = null
    private var fromName: String? = null
    private var toAccountId: UUID? = null
    private var toSortCode: String? = null
    private var toAccountNumber: String? = null
    private var toName: String? = null
    private var toIban: String? = null
    private var toBic: String? = null
    private var paymentPurpose: String? = null
    private var amountMinorUnits: Long = 0L
    private lateinit var currency: String
    private var description: String? = null
    private var returnReason: ReturnReason? = null
    private var fxConversion: FxConversionPayload? = null

    fun withPaymentId(paymentId: UUID) = apply { this.paymentId = paymentId }
    fun withState(state: String) = apply { this.state = state }
    fun withRail(rail: String) = apply { this.rail = rail }
    fun withDirection(direction: String) = apply { this.direction = direction }
    fun withCreatedAt(createdAt: Instant) = apply { this.createdAt = createdAt }
    fun withEndToEndId(endToEndId: UUID) = apply { this.endToEndId = endToEndId }
    fun withFromAccountId(fromAccountId: UUID?) = apply { this.fromAccountId = fromAccountId }
    fun withFromSortCode(fromSortCode: String?) = apply { this.fromSortCode = fromSortCode }
    fun withFromAccountNumber(fromAccountNumber: String?) = apply { this.fromAccountNumber = fromAccountNumber }
    fun withFromName(fromName: String?) = apply { this.fromName = fromName }
    fun withToAccountId(toAccountId: UUID?) = apply { this.toAccountId = toAccountId }
    fun withToSortCode(toSortCode: String?) = apply { this.toSortCode = toSortCode }
    fun withToAccountNumber(toAccountNumber: String?) = apply { this.toAccountNumber = toAccountNumber }
    fun withToName(toName: String?) = apply { this.toName = toName }
    fun withToIban(toIban: String?) = apply { this.toIban = toIban }
    fun withToBic(toBic: String?) = apply { this.toBic = toBic }
    fun withPaymentPurpose(paymentPurpose: String?) = apply { this.paymentPurpose = paymentPurpose }
    fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }
    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withReturnReason(returnReason: ReturnReason?) = apply { this.returnReason = returnReason }
    fun withFxConversion(amountMinorUnits: Long, currency: String, rateUsed: String) = apply {
        this.fxConversion = FxConversionPayload(amountMinorUnits, currency, rateUsed)
    }

    override fun build(): String = JsonSerialiser.toJson(
        Payload(
            paymentId = paymentId.toString(),
            state = state,
            rail = rail,
            direction = direction,
            createdAt = createdAt.toString(),
            endToEndId = endToEndId.toString(),
            fromAccountId = fromAccountId?.toString(),
            fromSortCode = fromSortCode,
            fromAccountNumber = fromAccountNumber,
            fromName = fromName,
            toAccountId = toAccountId?.toString(),
            toSortCode = toSortCode,
            toAccountNumber = toAccountNumber,
            toName = toName,
            toIban = toIban,
            toBic = toBic,
            paymentPurpose = paymentPurpose,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
            returnReason = returnReason?.name,
            fxConversion = fxConversion,
        )
    )

    private data class Payload(
        val paymentId: String,
        val state: String,
        val rail: String,
        val direction: String,
        val createdAt: String,
        val endToEndId: String,
        val fromAccountId: String?,
        val fromSortCode: String?,
        val fromAccountNumber: String?,
        val fromName: String?,
        val toAccountId: String?,
        val toSortCode: String?,
        val toAccountNumber: String?,
        val toName: String?,
        val toIban: String?,
        val toBic: String?,
        val paymentPurpose: String?,
        val amountMinorUnits: Long,
        val currency: String,
        val description: String?,
        val returnReason: String?,
        val fxConversion: FxConversionPayload?,
    )

    private data class FxConversionPayload(
        val amountMinorUnits: Long,
        val currency: String,
        val rateUsed: String,
    )

    companion object {
        fun anInitiatePaymentResponse() = InitiatePaymentResponseBuilder()
    }
}
