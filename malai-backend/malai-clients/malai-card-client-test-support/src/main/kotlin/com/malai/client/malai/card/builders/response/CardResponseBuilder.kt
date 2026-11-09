package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class CardResponseBuilder private constructor() : TestBuilder {

    private lateinit var cardId: UUID
    private lateinit var accountId: UUID
    private lateinit var maskedPan: String
    private lateinit var variant: String
    private lateinit var status: String
    private lateinit var expiry: String

    fun withCardId(cardId: UUID) = apply { this.cardId = cardId }
    fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
    fun withMaskedPan(maskedPan: String) = apply { this.maskedPan = maskedPan }
    fun withVariant(variant: String) = apply { this.variant = variant }
    fun withStatus(status: String) = apply { this.status = status }
    fun withExpiry(expiry: String) = apply { this.expiry = expiry }

    fun toModel() = Card(cardId, accountId, maskedPan, variant, status, expiry)

    override fun build(): String = JsonSerialiser.toJson(toModel())

    data class Card(
        val cardId: UUID,
        val accountId: UUID,
        val maskedPan: String,
        val variant: String,
        val status: String,
        val expiry: String,
    )

    companion object {
        fun aCardResponse() = CardResponseBuilder()
            .withCardId(UUID.randomUUID())
            .withAccountId(UUID.randomUUID())
            .withMaskedPan("************0000")
            .withVariant("DEBIT")
            .withStatus("ACTIVE")
            .withExpiry("10/29")
    }
}
