package com.malai.client.malai.accountholder.model

data class AddressesResponse(
    val current: AddressV2Response?,
    val previous: List<AddressV2Response>
)
