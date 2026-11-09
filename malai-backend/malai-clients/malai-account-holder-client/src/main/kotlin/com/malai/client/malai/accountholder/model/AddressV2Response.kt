package com.malai.client.malai.accountholder.model

data class AddressV2Response(
    val line1: String,
    val line2: String?,
    val line3: String?,
    val postTown: String,
    val postCode: String,
    val countryCode: String
)
