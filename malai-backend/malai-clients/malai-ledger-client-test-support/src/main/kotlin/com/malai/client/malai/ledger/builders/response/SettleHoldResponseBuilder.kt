package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class SettleHoldResponseBuilder private constructor() : TestBuilder {

    private lateinit var holdId: UUID
    private lateinit var status: String
    private lateinit var resolvedAt: String
    private lateinit var transaction: PostTransactionResponseBuilder

    fun withHoldId(holdId: UUID) = apply { this.holdId = holdId }
    fun withStatus(status: String) = apply { this.status = status }
    fun withResolvedAt(resolvedAt: String) = apply { this.resolvedAt = resolvedAt }
    fun withTransaction(transaction: PostTransactionResponseBuilder) = apply { this.transaction = transaction }

    override fun build(): String =
        JsonSerialiser.toJson(Settle(holdId, status, resolvedAt, transaction.toModel()))

    data class Settle(
        val holdId: UUID,
        val status: String,
        val resolvedAt: String,
        val transaction: PostTransactionResponseBuilder.Transaction,
    )

    companion object {
        fun aSettleHoldResponse() = SettleHoldResponseBuilder()
            .withHoldId(UUID.randomUUID())
            .withStatus("SETTLED")
            .withResolvedAt("2026-10-15T10:30:00Z")
    }
}
