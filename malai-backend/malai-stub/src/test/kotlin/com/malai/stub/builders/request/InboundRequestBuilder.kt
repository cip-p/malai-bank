package com.malai.stub.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class InboundRequestBuilder private constructor() : TestBuilder {

    private var beneficiarySortCode: String = "65-82-37"
    private var beneficiaryAccountNumber: String = "10000001"
    private var beneficiaryName: String = "Alice Walker"
    private var originatorSortCode: String = "20-00-05"
    private var originatorAccountNumber: String = "20000020"
    private var originatorName: String = "ACME Employer Ltd"
    private var amountMinorUnits: Long by Delegates.notNull()
    private var currency: String = "GBP"
    private var description: String? = null

    init {
        amountMinorUnits = 5_000L
    }

    fun withBeneficiarySortCode(sortCode: String) = apply { this.beneficiarySortCode = sortCode }
    fun withBeneficiaryAccountNumber(accountNumber: String) = apply { this.beneficiaryAccountNumber = accountNumber }
    fun withBeneficiaryName(name: String) = apply { this.beneficiaryName = name }
    fun withOriginatorSortCode(sortCode: String) = apply { this.originatorSortCode = sortCode }
    fun withOriginatorAccountNumber(accountNumber: String) = apply { this.originatorAccountNumber = accountNumber }
    fun withOriginatorName(name: String) = apply { this.originatorName = name }
    fun withAmountMinorUnits(amount: Long) = apply { this.amountMinorUnits = amount }
    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withDescription(description: String?) = apply { this.description = description }

    override fun build(): String = toJson(
        InboundRequestPayload(
            beneficiarySortCode = beneficiarySortCode,
            beneficiaryAccountNumber = beneficiaryAccountNumber,
            beneficiaryName = beneficiaryName,
            originatorSortCode = originatorSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
        )
    )

    private data class InboundRequestPayload(
        val beneficiarySortCode: String,
        val beneficiaryAccountNumber: String,
        val beneficiaryName: String,
        val originatorSortCode: String,
        val originatorAccountNumber: String,
        val originatorName: String,
        val amountMinorUnits: Long,
        val currency: String,
        val description: String?,
    )

    companion object {
        fun anInboundRequest() = InboundRequestBuilder()
    }
}
