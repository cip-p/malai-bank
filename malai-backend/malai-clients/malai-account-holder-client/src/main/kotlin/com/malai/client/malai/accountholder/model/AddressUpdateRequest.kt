package com.malai.client.malai.accountholder.model

data class AddressUpdateRequest(
    val line1: String,
    val line2: String?,
    val line3: String?,
    val postTown: String,
    val postCode: String,
    val countryCode: String,
    val udprn: String?,
    val umprn: String?,
    val from: String
)
