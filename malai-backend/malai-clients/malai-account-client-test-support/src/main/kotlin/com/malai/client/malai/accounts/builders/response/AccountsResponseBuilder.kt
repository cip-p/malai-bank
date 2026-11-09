package com.malai.client.malai.accounts.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class AccountsResponseBuilder private constructor() : TestBuilder {

    private lateinit var accounts: List<AccountResponseBuilder>

    fun withAccounts(vararg accounts: AccountResponseBuilder) = apply { this.accounts = accounts.toList() }

    override fun build(): String = JsonSerialiser.Companion.toJson(Accounts(accounts.map { it.build() }))

    data class Accounts(val accounts: List<AccountResponseBuilder.Account>)

    companion object {
        fun anAccounts() = AccountsResponseBuilder()
    }

    class AccountResponseBuilder private constructor() {
        private lateinit var accountUid: UUID
        private lateinit var currency: String

        fun withAccountUid(accountUid: UUID) = apply { this.accountUid = accountUid }
        fun withCurrency(currency: String) = apply { this.currency = currency }

        fun build() = Account(accountUid, currency)

        data class Account(val accountUid: UUID, val currency: String)

        companion object {
            fun anAccount() = AccountResponseBuilder()
        }
    }
}
