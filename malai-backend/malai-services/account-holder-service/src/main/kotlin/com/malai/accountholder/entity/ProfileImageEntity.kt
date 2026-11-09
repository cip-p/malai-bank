package com.malai.accountholder.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "profile_images")
class ProfileImageEntity(
    @Id
    val profileImageId: UUID,

    val accountHolderId: UUID,

    val contentType: String,

    val imageData: ByteArray
)
