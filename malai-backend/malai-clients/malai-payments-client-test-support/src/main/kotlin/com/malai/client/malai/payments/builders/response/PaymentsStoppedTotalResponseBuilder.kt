package com.malai.client.malai.payments.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class PaymentsStoppedTotalResponseBuilder private constructor() : TestBuilder {

    private var currency: String = "GBP"
    private var amountMinorUnits by Delegates.notNull<Long>()

    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }

    override fun build(): String = JsonSerialiser.toJson(PaymentsStoppedTotal(currency, amountMinorUnits))

    data class PaymentsStoppedTotal(val currency: String, val amountMinorUnits: Long)

    companion object {
        fun aPaymentsStoppedTotalResponse() = PaymentsStoppedTotalResponseBuilder().withAmountMinorUnits(0)
    }
}
