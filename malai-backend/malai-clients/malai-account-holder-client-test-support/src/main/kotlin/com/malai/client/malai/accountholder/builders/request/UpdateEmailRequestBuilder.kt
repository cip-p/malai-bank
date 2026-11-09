package com.malai.client.malai.accountholder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class UpdateEmailRequestBuilder private constructor() : TestBuilder {

    private lateinit var email: String

    fun withEmail(email: String) = apply { this.email = email }

    override fun build(): String = toJson(UpdateEmailRequestData(email))

    private data class UpdateEmailRequestData(val email: String)

    companion object {
        fun anUpdateEmailRequest() = UpdateEmailRequestBuilder()
    }
}
