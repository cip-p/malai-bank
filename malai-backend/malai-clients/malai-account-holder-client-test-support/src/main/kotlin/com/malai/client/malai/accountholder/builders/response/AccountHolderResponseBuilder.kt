package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.*

class AccountHolderResponseBuilder private constructor() : TestBuilder {

    private lateinit var accountHolderUid: UUID
    private lateinit var accountHolderType: String

    fun withAccountHolderUid(accountHolderUid: UUID) = apply { this.accountHolderUid = accountHolderUid }
    fun withAccountHolderType(accountHolderType: String) = apply { this.accountHolderType = accountHolderType }

    override fun build(): String = JsonSerialiser.toJson(AccountHolder(accountHolderUid, accountHolderType))

    data class AccountHolder(val accountHolderUid: UUID, val accountHolderType: String)

    companion object {
        fun anAccountHolder() = AccountHolderResponseBuilder()
    }
}
