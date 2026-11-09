package com.malai.client.malai.accountholder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class CreateAccountHolderRequestBuilder private constructor() : TestBuilder {

    private lateinit var accountHolderType: String

    fun withAccountHolderType(accountHolderType: String) = apply { this.accountHolderType = accountHolderType }

    override fun build(): String = toJson(CreateAccountHolderRequestData(accountHolderType))

    private data class CreateAccountHolderRequestData(val accountHolderType: String)

    companion object {
        fun aCreateAccountHolderRequest() = CreateAccountHolderRequestBuilder()
    }
}
