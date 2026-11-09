package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class AccountHolderNameResponseBuilder private constructor() : TestBuilder {

    private lateinit var accountHolderName: String

    fun withAccountHolderName(accountHolderName: String) = apply { this.accountHolderName = accountHolderName }

    override fun build(): String = JsonSerialiser.toJson(AccountHolderName(accountHolderName))

    data class AccountHolderName(val accountHolderName: String)

    companion object {
        fun anAccountHolderName() = AccountHolderNameResponseBuilder()
    }
}
