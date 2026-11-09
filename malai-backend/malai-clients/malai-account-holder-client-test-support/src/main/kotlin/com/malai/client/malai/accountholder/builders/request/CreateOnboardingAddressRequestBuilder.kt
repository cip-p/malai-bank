package com.malai.client.malai.accountholder.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class CreateOnboardingAddressRequestBuilder private constructor() : TestBuilder {

    private var line1: String? = null
    private var line2: String? = null
    private var city: String? = null
    private var postcode: String? = null
    private var country: String? = null

    fun withLine1(line1: String?) = apply { this.line1 = line1 }
    fun withLine2(line2: String?) = apply { this.line2 = line2 }
    fun withCity(city: String?) = apply { this.city = city }
    fun withPostcode(postcode: String?) = apply { this.postcode = postcode }
    fun withCountry(country: String?) = apply { this.country = country }

    override fun build(): String = toJson(Payload(line1, line2, city, postcode, country))

    private data class Payload(
        val line1: String?,
        val line2: String?,
        val city: String?,
        val postcode: String?,
        val country: String?,
    )

    companion object {
        fun aDefaultCreateOnboardingAddressRequest() = CreateOnboardingAddressRequestBuilder()
            .withLine1("10 Downing Street")
            .withLine2(null)
            .withCity("London")
            .withPostcode("SW1A 2AA")
            .withCountry("GB")
    }
}
