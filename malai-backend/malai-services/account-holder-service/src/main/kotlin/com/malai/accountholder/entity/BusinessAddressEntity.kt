package com.malai.accountholder.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "business_addresses")
class BusinessAddressEntity(
    @Id
    val addressId: UUID,

    val businessId: UUID,

    @Enumerated(EnumType.STRING)
    val addressType: AddressType,

    val line1: String,

    val line2: String?,

    val line3: String?,

    val postTown: String,

    val postCode: String,

    val countryCode: String
)
