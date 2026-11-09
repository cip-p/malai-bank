package com.malai.auth.entity


import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "users")
data class UserEntity(

    @Id
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String,
    val dateOfBirth: LocalDate?,
    @Enumerated(STRING)
    val kycStatus: KycStatus,
    val registrationConfirmed: Boolean,
    val createdAt: Instant,
    val createdBy: String,
    val modifiedAt: Instant? = null,
    val modifiedBy: String? = null
)

enum class KycStatus {
    PENDING,
    VERIFIED,
    FAILED
}
