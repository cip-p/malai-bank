package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class CardsIssuedResponseBuilder private constructor() : TestBuilder {

    private var count by Delegates.notNull<Long>()

    fun withCount(count: Long) = apply { this.count = count }

    override fun build(): String = JsonSerialiser.toJson(CardsIssued(count))

    data class CardsIssued(val count: Long)

    companion object {
        fun aCardsIssuedResponse() = CardsIssuedResponseBuilder().withCount(0)
    }
}
