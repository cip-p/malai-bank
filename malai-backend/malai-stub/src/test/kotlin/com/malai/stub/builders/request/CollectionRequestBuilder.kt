package com.malai.stub.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import java.util.UUID
import kotlin.properties.Delegates

class CollectionRequestBuilder private constructor() : TestBuilder {

    private var debtorSortCode: String = "65-82-37"
    private var debtorAccountNumber: String = "10000001"
    private var debtorName: String = "Alice Walker"
    private var creditorSortCode: String = "60-00-01"
    private var creditorAccountNumber: String = "22334455"
    private var creditorName: String = "Octopus Energy Ltd"
    private var amountMinorUnits: Long by Delegates.notNull()
    private var currency: String = "GBP"
    private var description: String? = null
    private var endToEndId: UUID? = null

    init {
        amountMinorUnits = 4_200L
    }

    fun withDebtorSortCode(sortCode: String) = apply { this.debtorSortCode = sortCode }
    fun withDebtorAccountNumber(accountNumber: String) = apply { this.debtorAccountNumber = accountNumber }
    fun withDebtorName(name: String) = apply { this.debtorName = name }
    fun withCreditorSortCode(sortCode: String) = apply { this.creditorSortCode = sortCode }
    fun withCreditorAccountNumber(accountNumber: String) = apply { this.creditorAccountNumber = accountNumber }
    fun withCreditorName(name: String) = apply { this.creditorName = name }
    fun withAmountMinorUnits(amount: Long) = apply { this.amountMinorUnits = amount }
    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withEndToEndId(endToEndId: UUID?) = apply { this.endToEndId = endToEndId }

    override fun build(): String = toJson(
        CollectionRequestPayload(
            debtorSortCode = debtorSortCode,
            debtorAccountNumber = debtorAccountNumber,
            debtorName = debtorName,
            creditorSortCode = creditorSortCode,
            creditorAccountNumber = creditorAccountNumber,
            creditorName = creditorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
            endToEndId = endToEndId,
        )
    )

    private data class CollectionRequestPayload(
        val debtorSortCode: String,
        val debtorAccountNumber: String,
        val debtorName: String,
        val creditorSortCode: String,
        val creditorAccountNumber: String,
        val creditorName: String,
        val amountMinorUnits: Long,
        val currency: String,
        val description: String?,
        val endToEndId: UUID?,
    )

    companion object {
        fun aCollectionRequest() = CollectionRequestBuilder()
    }
}
