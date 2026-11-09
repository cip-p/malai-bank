package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class HoldResponseBuilder private constructor() : TestBuilder {

    private lateinit var holdId: UUID
    private lateinit var accountId: UUID
    private var amountMinorUnits: Long = 0
    private lateinit var status: String
    private lateinit var placedAt: String
    private var resolvedAt: String? = null

    fun withHoldId(holdId: UUID) = apply { this.holdId = holdId }
    fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
    fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }
    fun withStatus(status: String) = apply { this.status = status }
    fun withPlacedAt(placedAt: String) = apply { this.placedAt = placedAt }
    fun withResolvedAt(resolvedAt: String?) = apply { this.resolvedAt = resolvedAt }

    override fun build(): String =
        JsonSerialiser.toJson(Hold(holdId, accountId, amountMinorUnits, status, placedAt, resolvedAt))

    data class Hold(
        val holdId: UUID,
        val accountId: UUID,
        val amountMinorUnits: Long,
        val status: String,
        val placedAt: String,
        val resolvedAt: String?,
    )

    companion object {
        fun aHoldResponse() = HoldResponseBuilder()
            .withHoldId(UUID.randomUUID())
            .withAccountId(UUID.randomUUID())
            .withAmountMinorUnits(0L)
            .withStatus("PLACED")
            .withPlacedAt("2026-10-15T10:30:00Z")
    }
}
