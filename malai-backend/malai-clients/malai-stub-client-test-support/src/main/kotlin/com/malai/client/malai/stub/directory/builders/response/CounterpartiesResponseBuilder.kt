package com.malai.client.malai.stub.directory.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class CounterpartiesResponseBuilder private constructor() : TestBuilder {

    private val counterparties = mutableListOf<CounterpartyResponseBuilder>()

    fun withCounterparties(vararg builders: CounterpartyResponseBuilder) = apply {
        counterparties.clear()
        counterparties.addAll(builders)
    }

    override fun build(): String = JsonSerialiser.toJson(
        mapOf("counterparties" to counterparties.map { it.toMap() })
    )

    companion object {
        fun aCounterpartiesResponse() = CounterpartiesResponseBuilder()
    }
}
