package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class CardsResponseBuilder private constructor() : TestBuilder {

    private val cards: MutableList<CardResponseBuilder> = mutableListOf()

    fun withCards(vararg cards: CardResponseBuilder) = apply { this.cards.addAll(cards) }

    override fun build(): String = JsonSerialiser.toJson(Cards(cards.map { it.toModel() }))

    data class Cards(val cards: List<CardResponseBuilder.Card>)

    companion object {
        fun aCardsResponse() = CardsResponseBuilder()
    }
}
