package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class AccountHoldersCreatedResponseBuilder private constructor() : TestBuilder {

    private var count by Delegates.notNull<Long>()

    fun withCount(count: Long) = apply { this.count = count }

    override fun build(): String = JsonSerialiser.toJson(AccountHoldersCreated(count))

    data class AccountHoldersCreated(val count: Long)

    companion object {
        fun anAccountHoldersCreatedResponse() = AccountHoldersCreatedResponseBuilder().withCount(0)
    }
}
