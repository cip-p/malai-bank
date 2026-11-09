package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class BalancesResponseBuilder private constructor() : TestBuilder {

    private lateinit var accountId: UUID
    private var ledgerBalanceMinorUnits: Long = 0
    private var heldAmountMinorUnits: Long = 0
    private var availableBalanceMinorUnits: Long = 0

    fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
    fun withLedgerBalanceMinorUnits(value: Long) = apply { this.ledgerBalanceMinorUnits = value }
    fun withHeldAmountMinorUnits(value: Long) = apply { this.heldAmountMinorUnits = value }
    fun withAvailableBalanceMinorUnits(value: Long) = apply { this.availableBalanceMinorUnits = value }

    override fun build(): String = JsonSerialiser.toJson(
        Balances(accountId, ledgerBalanceMinorUnits, heldAmountMinorUnits, availableBalanceMinorUnits)
    )

    data class Balances(
        val accountId: UUID,
        val ledgerBalanceMinorUnits: Long,
        val heldAmountMinorUnits: Long,
        val availableBalanceMinorUnits: Long,
    )

    companion object {
        fun aBalancesResponse() = BalancesResponseBuilder()
            .withAccountId(UUID.randomUUID())
    }
}
