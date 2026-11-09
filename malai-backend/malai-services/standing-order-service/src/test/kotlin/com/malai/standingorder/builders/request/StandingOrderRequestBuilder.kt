package com.malai.standingorder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import java.util.UUID

/**
 * Test-scope builder for `POST /api/v1/standing-orders` (SO/DD lifecycle issue 03).
 *
 * Lives in `standing-order-service` test sources rather than a client-test-support
 * module for the same reason as `PayeeRequestBuilder`: no cross-service consumers
 * yet. Promote when the e2e suite or another service needs it.
 */
class StandingOrderRequestBuilder private constructor() : TestBuilder {

    private var accountId: UUID? = null
    private var payeeId: UUID? = null
    private var amountMinorUnits: Long? = null
    private var currency: String? = null
    private var reference: String? = null
    private var frequency: String? = null
    private var startDate: String? = null

    fun withAccountId(accountId: UUID?) = apply { this.accountId = accountId }
    fun withPayeeId(payeeId: UUID?) = apply { this.payeeId = payeeId }
    fun withAmountMinorUnits(amountMinorUnits: Long?) = apply { this.amountMinorUnits = amountMinorUnits }
    fun withCurrency(currency: String?) = apply { this.currency = currency }
    fun withReference(reference: String?) = apply { this.reference = reference }
    fun withFrequency(frequency: String?) = apply { this.frequency = frequency }
    fun withStartDate(startDate: String?) = apply { this.startDate = startDate }

    override fun build(): String = toJson(
        StandingOrderRequestPayload(accountId, payeeId, amountMinorUnits, currency, reference, frequency, startDate)
    )

    private data class StandingOrderRequestPayload(
        val accountId: UUID?,
        val payeeId: UUID?,
        val amountMinorUnits: Long?,
        val currency: String?,
        val reference: String?,
        val frequency: String?,
        val startDate: String?,
    )

    companion object {
        fun aStandingOrderRequest() = StandingOrderRequestBuilder()
            .withAmountMinorUnits(95000)
            .withCurrency("GBP")
            .withReference("SO-RENT")
            .withFrequency("MONTHLY")
            .withStartDate("2026-07-15")
    }
}
