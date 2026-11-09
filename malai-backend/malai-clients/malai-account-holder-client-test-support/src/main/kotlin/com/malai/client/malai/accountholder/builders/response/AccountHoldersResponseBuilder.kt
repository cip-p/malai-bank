package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.*

class AccountHoldersResponseBuilder private constructor() : TestBuilder {

    private var accountHolders: List<AccountHolderSummaryBuilder> = emptyList()

    fun withAccountHolders(vararg accountHolders: AccountHolderSummaryBuilder) = apply {
        this.accountHolders = accountHolders.toList()
    }

    override fun build(): String = JsonSerialiser.toJson(
        AccountHolders(accountHolders.map { it.buildSummary() })
    )

    data class AccountHolders(val accountHolders: List<AccountHolderSummary>)

    data class AccountHolderSummary(
        val accountHolderUid: UUID,
        val accountHolderName: String,
        val accountHolderType: String
    )

    class AccountHolderSummaryBuilder private constructor() {

        private lateinit var accountHolderUid: UUID
        private lateinit var accountHolderName: String
        private lateinit var accountHolderType: String

        fun withAccountHolderUid(accountHolderUid: UUID) = apply { this.accountHolderUid = accountHolderUid }
        fun withAccountHolderName(accountHolderName: String) = apply { this.accountHolderName = accountHolderName }
        fun withAccountHolderType(accountHolderType: String) = apply { this.accountHolderType = accountHolderType }

        fun buildSummary() = AccountHolderSummary(accountHolderUid, accountHolderName, accountHolderType)

        companion object {
            fun anAccountHolderSummary() = AccountHolderSummaryBuilder()
        }
    }

    companion object {
        fun anAccountHoldersResponse() = AccountHoldersResponseBuilder()
    }
}
