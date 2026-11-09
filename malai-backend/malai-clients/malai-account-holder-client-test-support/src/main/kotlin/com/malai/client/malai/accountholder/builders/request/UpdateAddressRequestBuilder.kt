package com.malai.client.malai.accountholder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class UpdateAddressRequestBuilder private constructor() : TestBuilder {

    private lateinit var line1: String
    private var line2: String? = null
    private lateinit var postTown: String
    private lateinit var postCode: String
    private lateinit var countryCode: String
    private lateinit var from: String

    fun withLine1(line1: String) = apply { this.line1 = line1 }
    fun withLine2(line2: String) = apply { this.line2 = line2 }
    fun withPostTown(postTown: String) = apply { this.postTown = postTown }
    fun withPostCode(postCode: String) = apply { this.postCode = postCode }
    fun withCountryCode(countryCode: String) = apply { this.countryCode = countryCode }
    fun withFrom(from: String) = apply { this.from = from }

    override fun build(): String = toJson(UpdateAddressRequestData(line1, line2, postTown, postCode, countryCode, from))

    private data class UpdateAddressRequestData(
        val line1: String,
        val line2: String?,
        val postTown: String,
        val postCode: String,
        val countryCode: String,
        val from: String
    )

    companion object {
        fun anUpdateAddressRequest() = UpdateAddressRequestBuilder()
    }
}
