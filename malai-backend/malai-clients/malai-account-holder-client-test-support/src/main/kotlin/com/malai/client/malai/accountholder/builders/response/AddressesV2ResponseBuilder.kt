package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class AddressesV2ResponseBuilder private constructor() : TestBuilder {

    private var current: AddressV2ResponseBuilder? = null
    private var previous: List<AddressV2ResponseBuilder> = emptyList()

    fun withCurrent(current: AddressV2ResponseBuilder?) = apply { this.current = current }
    fun withPrevious(vararg previous: AddressV2ResponseBuilder) = apply { this.previous = previous.toList() }

    override fun build(): String = JsonSerialiser.toJson(
        AddressesV2(
            current = current?.buildAddress(),
            previous = previous.map { it.buildAddress() }
        )
    )

    data class AddressesV2(
        val current: AddressV2ResponseBuilder.AddressV2?,
        val previous: List<AddressV2ResponseBuilder.AddressV2>
    )

    companion object {
        fun anAddresses() = AddressesV2ResponseBuilder()
    }
}
