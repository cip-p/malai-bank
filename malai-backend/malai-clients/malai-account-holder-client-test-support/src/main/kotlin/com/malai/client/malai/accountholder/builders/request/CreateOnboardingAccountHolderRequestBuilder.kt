package com.malai.client.malai.accountholder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import java.util.UUID

class CreateOnboardingAccountHolderRequestBuilder private constructor() : TestBuilder {

    private var userId: UUID? = null

    fun withUserId(userId: UUID?) = apply { this.userId = userId }

    override fun build(): String = toJson(Payload(userId?.toString()))

    private data class Payload(val userId: String?)

    companion object {
        fun aDefaultCreateOnboardingAccountHolderRequest() =
            CreateOnboardingAccountHolderRequestBuilder().withUserId(UUID.randomUUID())
    }
}
