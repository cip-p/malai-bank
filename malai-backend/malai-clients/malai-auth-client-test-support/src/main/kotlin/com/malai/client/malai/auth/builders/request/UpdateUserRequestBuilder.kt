package com.malai.client.malai.auth.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class UpdateUserRequestBuilder private constructor() : TestBuilder {

    private var firstName: String? = null
    private var lastName: String? = null

    fun withFirstName(firstName: String?) = apply { this.firstName = firstName }
    fun withLastName(lastName: String?) = apply { this.lastName = lastName }

    override fun build(): String = toJson(UpdateUserRequest(firstName, lastName))

    data class UpdateUserRequest(
        val firstName: String?,
        val lastName: String?,
    )

    companion object {
        fun aDefaultUpdateUserRequest() = UpdateUserRequestBuilder()
            .withFirstName("default-first-name")
            .withLastName("default-last-name")
    }
}
