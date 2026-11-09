package com.malai.client.malai.auth.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class LoginRequestBuilder private constructor() : TestBuilder {

    private var email: String? = null

    fun withEmail(email: String?) = apply { this.email = email }

    override fun build(): String = toJson(LoginRequest(email))

    data class LoginRequest(
        val email: String?,
    )

    companion object {
        fun aDefaultLoginRequest() = LoginRequestBuilder()
            .withEmail("default-email@example.com")
    }
}
