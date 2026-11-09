package com.malai.accountholder.entity

import com.malai.commons.model.OnboardingStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "individuals")
data class IndividualEntity(
    @Id
    val individualId: UUID,

    val accountHolderId: UUID,

    val title: String?,

    val firstName: String,

    val lastName: String,

    val dateOfBirth: LocalDate,

    val email: String,

    val phone: String,

    @Enumerated(STRING)
    val status: OnboardingStatus,
)
