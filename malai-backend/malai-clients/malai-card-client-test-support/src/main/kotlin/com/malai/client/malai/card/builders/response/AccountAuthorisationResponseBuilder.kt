package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID
import kotlin.properties.Delegates

class AccountAuthorisationResponseBuilder private constructor() : TestBuilder {

    private lateinit var authorisationId: UUID
    private var cardId: UUID? = null
    private lateinit var status: String
    private lateinit var merchantName: String
    private var amountMinorUnits: Long by Delegates.notNull()
    private lateinit var currency: String
    private var declineReason: String? = null
    private lateinit var createdAt: String

    fun withAuthorisationId(authorisationId: UUID) = apply { this.authorisationId = authorisationId }
    fun withCardId(cardId: UUID?) = apply { this.cardId = cardId }
    fun withStatus(status: String) = apply { this.status = status }
    fun withMerchantName(merchantName: String) = apply { this.merchantName = merchantName }
    fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }
    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withDeclineReason(declineReason: String?) = apply { this.declineReason = declineReason }
    fun withCreatedAt(createdAt: String) = apply { this.createdAt = createdAt }

    fun toModel() = AccountAuthorisation(
        authorisationId, cardId, status, merchantName, amountMinorUnits, currency, declineReason, createdAt,
    )

    override fun build(): String = JsonSerialiser.toJson(toModel())

    data class AccountAuthorisation(
        val authorisationId: UUID,
        val cardId: UUID?,
        val status: String,
        val merchantName: String,
        val amountMinorUnits: Long,
        val currency: String,
        val declineReason: String?,
        val createdAt: String,
    )

    companion object {
        fun anAccountAuthorisation() = AccountAuthorisationResponseBuilder()
            .withAuthorisationId(UUID.randomUUID())
            .withCardId(UUID.randomUUID())
            .withStatus("AUTHORISED")
            .withMerchantName("Fictional Coffee Co")
            .withAmountMinorUnits(4_370)
            .withCurrency("GBP")
            .withCreatedAt("2026-10-15T10:00:00Z")
    }
}
