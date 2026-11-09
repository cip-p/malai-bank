package com.malai.client.malai.stub.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class CardMerchantsResponseBuilder private constructor() : TestBuilder {

    private val merchants = mutableListOf<CardMerchantResponseBuilder>()

    fun withMerchants(vararg builders: CardMerchantResponseBuilder) = apply {
        merchants.clear()
        merchants.addAll(builders)
    }

    override fun build(): String = JsonSerialiser.toJson(
        mapOf("merchants" to merchants.map { it.toMap() })
    )

    companion object {
        fun aCardMerchantsResponse() = CardMerchantsResponseBuilder()
    }
}
