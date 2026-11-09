package com.malai.payee.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payees")
data class PayeeEntity(

    @Id
    @Column(name = "payee_id")
    val payeeId: UUID,

    @Column(name = "account_holder_id")
    val accountHolderId: UUID,

    val name: String,

    @Column(name = "sort_code")
    val sortCode: String,

    @Column(name = "account_number")
    val accountNumber: String,

    val nickname: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "created_by")
    val createdBy: UUID,
)
