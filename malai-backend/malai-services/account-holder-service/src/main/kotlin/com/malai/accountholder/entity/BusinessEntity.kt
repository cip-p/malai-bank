package com.malai.accountholder.entity

import com.malai.commons.model.OnboardingStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "businesses")
class BusinessEntity(
    @Id
    val businessId: UUID,

    val accountHolderId: UUID,

    val companyName: String,

    val companyType: String?,

    val companyCategory: String?,

    val companySubCategory: String?,

    val companyRegistrationNumber: String?,

    val email: String,

    val phone: String,

    @Enumerated(STRING)
    val status: OnboardingStatus,
)
