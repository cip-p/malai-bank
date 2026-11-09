package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class AddressV2ResponseBuilder private constructor() : TestBuilder {

    private lateinit var line1: String
    private var line2: String? = null
    private var line3: String? = null
    private lateinit var postTown: String
    private lateinit var postCode: String
    private lateinit var countryCode: String

    fun withLine1(line1: String) = apply { this.line1 = line1 }
    fun withLine2(line2: String?) = apply { this.line2 = line2 }
    fun withLine3(line3: String?) = apply { this.line3 = line3 }
    fun withPostTown(postTown: String) = apply { this.postTown = postTown }
    fun withPostCode(postCode: String) = apply { this.postCode = postCode }
    fun withCountryCode(countryCode: String) = apply { this.countryCode = countryCode }

    override fun build(): String = JsonSerialiser.toJson(buildAddress())

    fun buildAddress(): AddressV2 = AddressV2(line1, line2, line3, postTown, postCode, countryCode)

    data class AddressV2(
        val line1: String,
        val line2: String?,
        val line3: String?,
        val postTown: String,
        val postCode: String,
        val countryCode: String
    )

    companion object {
        fun anAddress() = AddressV2ResponseBuilder()
    }
}
