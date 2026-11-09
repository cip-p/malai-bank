package com.malai.client.malai.standingorder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class ActiveStandingOrdersResponseBuilder private constructor() : TestBuilder {

    private val standingOrders = mutableListOf<ActiveStandingOrderBuilder>()

    fun withStandingOrders(vararg builders: ActiveStandingOrderBuilder) =
        apply { standingOrders.addAll(builders) }

    override fun build(): String = JsonSerialiser.toJson(
        ActiveStandingOrders(standingOrders.map { it.toModel() }),
    )

    data class ActiveStandingOrders(val standingOrders: List<ActiveStandingOrder>)

    data class ActiveStandingOrder(
        val standingOrderId: UUID,
        val accountId: UUID,
        val payeeName: String,
        val amountMinorUnits: Long,
        val currency: String,
        val frequency: String,
        val status: String,
        val startDate: String,
    )

    class ActiveStandingOrderBuilder private constructor() {
        private var standingOrderId: UUID = UUID.randomUUID()
        private var accountId: UUID = UUID.randomUUID()
        private var payeeName: String = "Landlord"
        private var amountMinorUnits: Long = 95_000
        private var currency: String = "GBP"
        private var frequency: String = "MONTHLY"
        private var status: String = "ACTIVE"
        private var startDate: String = "2026-06-01"

        fun withStandingOrderId(value: UUID) = apply { this.standingOrderId = value }
        fun withAccountId(value: UUID) = apply { this.accountId = value }
        fun withPayeeName(value: String) = apply { this.payeeName = value }
        fun withAmountMinorUnits(value: Long) = apply { this.amountMinorUnits = value }
        fun withCurrency(value: String) = apply { this.currency = value }
        fun withFrequency(value: String) = apply { this.frequency = value }
        fun withStatus(value: String) = apply { this.status = value }
        fun withStartDate(value: String) = apply { this.startDate = value }

        fun toModel() = ActiveStandingOrder(
            standingOrderId = standingOrderId,
            accountId = accountId,
            payeeName = payeeName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            frequency = frequency,
            status = status,
            startDate = startDate,
        )

        companion object {
            fun anActiveStandingOrder() = ActiveStandingOrderBuilder()
        }
    }

    companion object {
        fun anActiveStandingOrdersResponse() = ActiveStandingOrdersResponseBuilder()
    }
}
