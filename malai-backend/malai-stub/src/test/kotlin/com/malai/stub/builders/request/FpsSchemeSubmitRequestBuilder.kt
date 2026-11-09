package com.malai.stub.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import com.malai.scheme.contracts.fps.FpsSchemeAmount
import com.malai.scheme.contracts.fps.FpsSchemeParty
import com.malai.scheme.contracts.fps.FpsSchemeSubmitRequest
import java.util.UUID
import kotlin.properties.Delegates

class FpsSchemeSubmitRequestBuilder private constructor() : TestBuilder {

    private lateinit var endToEndId: UUID
    private lateinit var paymentId: UUID
    private var debtorSortCode: String? = "65-82-37"
    private var debtorAccountNumber: String? = "10000001"
    private var debtorName: String? = "Alice"
    private var creditorSortCode: String? = "60-00-01"
    private var creditorAccountNumber: String? = "12345678"
    private var creditorName: String? = "Bob"
    private var instructedAmountMinorUnits: Long by Delegates.notNull()
    private var instructedAmountCurrency: String = "GBP"
    private var remittanceInformation: String? = "test"
    private var copVerdictOverride: Boolean by Delegates.notNull()

    init {
        instructedAmountMinorUnits = 1000L
        copVerdictOverride = false
    }

    fun withEndToEndId(endToEndId: UUID) = apply { this.endToEndId = endToEndId }
    fun withPaymentId(paymentId: UUID) = apply { this.paymentId = paymentId }
    fun withCreditorSortCode(sortCode: String?) = apply { this.creditorSortCode = sortCode }
    fun withCreditorAccountNumber(accountNumber: String?) = apply { this.creditorAccountNumber = accountNumber }
    fun withCreditorName(name: String?) = apply { this.creditorName = name }
    fun withCopVerdictOverride(override: Boolean) = apply { this.copVerdictOverride = override }

    override fun build(): String = toJson(
        FpsSchemeSubmitRequest(
            endToEndId = endToEndId,
            paymentId = paymentId,
            debtor = FpsSchemeParty(debtorSortCode, debtorAccountNumber, debtorName),
            creditor = FpsSchemeParty(creditorSortCode, creditorAccountNumber, creditorName),
            instructedAmount = FpsSchemeAmount(instructedAmountMinorUnits, instructedAmountCurrency),
            remittanceInformation = remittanceInformation,
            copVerdictOverride = copVerdictOverride,
        )
    )

    companion object {
        fun anFpsSchemeSubmitRequest() = FpsSchemeSubmitRequestBuilder()
    }
}
