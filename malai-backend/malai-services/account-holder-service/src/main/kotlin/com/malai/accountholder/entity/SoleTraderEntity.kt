package com.malai.accountholder.entity

import com.malai.commons.model.OnboardingStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "sole_traders")
class SoleTraderEntity(
    @Id
    val soleTraderId: UUID,

    val accountHolderId: UUID,

    val tradingAsName: String,

    val businessCategory: String?,

    val businessSubCategory: String?,

    @Enumerated(STRING)
    val status: OnboardingStatus
)
