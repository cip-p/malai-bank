package com.malai.accountholder.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "settle_up_profiles")
class SettleUpProfileEntity(

    @Id
    val settleUpProfileId: UUID,

    val accountHolderId: UUID,

    val settleUpLink: String,

    @Enumerated(EnumType.STRING)
    val status: SettleUpProfileStatus
)

enum class SettleUpProfileStatus {
    ACTIVE,
    INACTIVE,
    CLOSED
}
