package com.malai.client.malai.accountholder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class CreateOnboardingIndividualRequestBuilder private constructor() : TestBuilder {

    private var firstName: String? = null
    private var lastName: String? = null
    private var dateOfBirth: String? = null
    private var email: String? = null

    fun withFirstName(firstName: String?) = apply { this.firstName = firstName }
    fun withLastName(lastName: String?) = apply { this.lastName = lastName }
    fun withDateOfBirth(dateOfBirth: String?) = apply { this.dateOfBirth = dateOfBirth }
    fun withEmail(email: String?) = apply { this.email = email }

    override fun build(): String = toJson(Payload(firstName, lastName, dateOfBirth, email))

    private data class Payload(
        val firstName: String?,
        val lastName: String?,
        val dateOfBirth: String?,
        val email: String?,
    )

    companion object {
        fun aDefaultCreateOnboardingIndividualRequest() = CreateOnboardingIndividualRequestBuilder()
            .withFirstName("Alice")
            .withLastName("Smith")
            .withDateOfBirth("1990-04-12")
            .withEmail("alice@example.com")
    }
}
