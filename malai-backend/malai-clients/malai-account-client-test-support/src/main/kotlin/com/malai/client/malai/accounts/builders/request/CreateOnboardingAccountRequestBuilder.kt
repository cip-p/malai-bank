package com.malai.client.malai.accounts.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import java.util.UUID

class CreateOnboardingAccountRequestBuilder private constructor() : TestBuilder {

    private var userId: UUID? = null
    private var accountHolderId: UUID? = null
    private var currency: String? = null
    private var name: String? = null

    fun withUserId(userId: UUID?) = apply { this.userId = userId }
    fun withAccountHolderId(accountHolderId: UUID?) = apply { this.accountHolderId = accountHolderId }
    fun withCurrency(currency: String?) = apply { this.currency = currency }
    fun withName(name: String?) = apply { this.name = name }

    override fun build(): String =
        toJson(Payload(userId?.toString(), accountHolderId?.toString(), currency, name))

    private data class Payload(
        val userId: String?,
        val accountHolderId: String?,
        val currency: String?,
        val name: String?,
    )

    companion object {
        fun aDefaultCreateOnboardingAccountRequest() = CreateOnboardingAccountRequestBuilder()
            .withUserId(UUID.randomUUID())
            .withAccountHolderId(UUID.randomUUID())
            .withCurrency("GBP")
            .withName("Personal")
    }
}
