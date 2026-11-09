package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class CardDetailResponseBuilder private constructor() : TestBuilder {

    private lateinit var cardId: UUID
    private lateinit var accountId: UUID
    private lateinit var cardholderName: String
    private lateinit var pan: String
    private lateinit var expiry: String
    private lateinit var cvv: String
    private lateinit var variant: String
    private lateinit var status: String

    fun withCardId(cardId: UUID) = apply { this.cardId = cardId }
    fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
    fun withCardholderName(cardholderName: String) = apply { this.cardholderName = cardholderName }
    fun withPan(pan: String) = apply { this.pan = pan }
    fun withExpiry(expiry: String) = apply { this.expiry = expiry }
    fun withCvv(cvv: String) = apply { this.cvv = cvv }
    fun withVariant(variant: String) = apply { this.variant = variant }
    fun withStatus(status: String) = apply { this.status = status }

    override fun build(): String =
        JsonSerialiser.toJson(CardDetail(cardId, accountId, cardholderName, pan, expiry, cvv, variant, status))

    data class CardDetail(
        val cardId: UUID,
        val accountId: UUID,
        val cardholderName: String,
        val pan: String,
        val expiry: String,
        val cvv: String,
        val variant: String,
        val status: String,
    )

    companion object {
        fun aCardDetailResponse() = CardDetailResponseBuilder()
            .withCardId(UUID.randomUUID())
            .withAccountId(UUID.randomUUID())
            .withCardholderName("Some Cardholder")
            .withPan("9904320000000000")
            .withExpiry("10/29")
            .withCvv("000")
            .withVariant("DEBIT")
            .withStatus("ACTIVE")
    }
}
