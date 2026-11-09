package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class RegisterLedgerAccountResponseBuilder private constructor() : TestBuilder {

    private lateinit var accountId: UUID
    private lateinit var currency: String
    private lateinit var kind: String
    private lateinit var guardPolicy: String
    private var floorMinorUnits: Long? = null

    fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withKind(kind: String) = apply { this.kind = kind }
    fun withGuardPolicy(guardPolicy: String) = apply { this.guardPolicy = guardPolicy }
    fun withFloorMinorUnits(floorMinorUnits: Long?) = apply { this.floorMinorUnits = floorMinorUnits }

    override fun build(): String = JsonSerialiser.toJson(LedgerAccount(accountId, currency, kind, guardPolicy, floorMinorUnits))

    data class LedgerAccount(
        val accountId: UUID,
        val currency: String,
        val kind: String,
        val guardPolicy: String,
        val floorMinorUnits: Long?,
    )

    companion object {
        fun aRegisterLedgerAccountResponse() = RegisterLedgerAccountResponseBuilder()
            .withAccountId(UUID.randomUUID())
            .withCurrency("GBP")
            .withKind("CUSTOMER")
            .withGuardPolicy("NO_OVERDRAFT")
    }
}
