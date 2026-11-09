package com.malai.accountholder.entity

import com.malai.commons.model.OnboardingStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

@Entity
@Table(name = "account_holders")
data class AccountHolderEntity(
    @Id
    val accountHolderId: UUID,

    val userId: UUID,

    @Enumerated(STRING)
    val accountHolderType: AccountHolderType,

    val createdAt: Instant,

    @Enumerated(STRING)
    val status: OnboardingStatus,
)
