package com.malai.accountholder.repository

import com.malai.accountholder.entity.ProfileImageEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface ProfileImageRepository : JpaRepository<ProfileImageEntity, UUID> {

    fun findByAccountHolderId(accountHolderId: UUID): ProfileImageEntity?

    fun deleteByAccountHolderId(accountHolderId: UUID)
}
