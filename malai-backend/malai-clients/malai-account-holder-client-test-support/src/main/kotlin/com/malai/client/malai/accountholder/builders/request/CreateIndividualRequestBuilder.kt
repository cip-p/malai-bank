package com.malai.client.malai.accountholder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class CreateIndividualRequestBuilder private constructor() : TestBuilder {

    private var title: String? = null
    private lateinit var firstName: String
    private lateinit var lastName: String
    private lateinit var dateOfBirth: String
    private lateinit var email: String
    private lateinit var phone: String

    fun withTitle(title: String?) = apply { this.title = title }
    fun withFirstName(firstName: String) = apply { this.firstName = firstName }
    fun withLastName(lastName: String) = apply { this.lastName = lastName }
    fun withDateOfBirth(dateOfBirth: String) = apply { this.dateOfBirth = dateOfBirth }
    fun withEmail(email: String) = apply { this.email = email }
    fun withPhone(phone: String) = apply { this.phone = phone }

    override fun build(): String = toJson(CreateIndividualRequestData(title, firstName, lastName, dateOfBirth, email, phone))

    private data class CreateIndividualRequestData(
        val title: String?,
        val firstName: String,
        val lastName: String,
        val dateOfBirth: String,
        val email: String,
        val phone: String
    )

    companion object {
        fun aCreateIndividualRequest() = CreateIndividualRequestBuilder()
    }
}
