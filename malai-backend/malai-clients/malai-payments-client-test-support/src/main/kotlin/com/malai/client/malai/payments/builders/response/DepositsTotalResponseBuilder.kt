package com.malai.client.malai.payments.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class DepositsTotalResponseBuilder private constructor() : TestBuilder {

    private var currency: String = "GBP"
    private var amountMinorUnits by Delegates.notNull<Long>()

    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }

    override fun build(): String = JsonSerialiser.toJson(DepositsTotal(currency, amountMinorUnits))

    data class DepositsTotal(val currency: String, val amountMinorUnits: Long)

    companion object {
        fun aDepositsTotalResponse() = DepositsTotalResponseBuilder().withAmountMinorUnits(0)
    }
}
