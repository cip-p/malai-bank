package com.malai.client.malai.auth.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class RegisterUserRequestBuilder private constructor() : TestBuilder {

    private var firstName: String? = null
    private var lastName: String? = null
    private var email: String? = null

    fun withFirstName(firstName: String?) = apply { this.firstName = firstName }
    fun withLastName(lastName: String?) = apply { this.lastName = lastName }
    fun withEmail(email: String?) = apply { this.email = email }

    override fun build(): String = toJson(
        RegisterRequestPayload(
            firstName = firstName,
            lastName = lastName,
            email = email
        )
    )

    data class RegisterRequestPayload(
        val firstName: String?,
        val lastName: String?,
        val email: String?
    )

    companion object {
        fun aDefaultRegisterUserRequest() = RegisterUserRequestBuilder()
            .withFirstName("default-first-name")
            .withLastName("default-last-name")
            .withEmail("default-email@example.com")
    }
}
