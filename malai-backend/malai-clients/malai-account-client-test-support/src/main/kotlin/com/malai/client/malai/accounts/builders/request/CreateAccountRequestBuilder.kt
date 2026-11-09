package com.malai.client.malai.accounts.builders.request

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class CreateAccountRequestBuilder private constructor() : TestBuilder {

    private lateinit var accountType: String
    private lateinit var currency: String
    private lateinit var name: String

    fun withAccountType(accountType: String) = apply { this.accountType = accountType }
    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withName(name: String) = apply { this.name = name }

    override fun build(): String =
        JsonSerialiser.toJson(CreateAccountRequest(accountType, currency, name))

    data class CreateAccountRequest(
        val accountType: String,
        val currency: String,
        val name: String
    )

    companion object {
        fun aDefaultCreateAccountRequest() = CreateAccountRequestBuilder()
            .withAccountType("PRIMARY")
            .withCurrency("GBP")
            .withName("Personal")
    }
}
