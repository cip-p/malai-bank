package com.malai.accountholder.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "account_holder_addresses")
class AddressEntity(
    @Id
    val addressId: UUID,

    val accountHolderId: UUID,

    val line1: String,

    val line2: String?,

    val line3: String?,

    val postTown: String,

    val postCode: String,

    val countryCode: String,

    val udprn: String?,

    val umprn: String?,

    val fromDate: LocalDate,

    val isCurrent: Boolean
)
