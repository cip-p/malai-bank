package com.malai.accountholder.repository

import com.malai.accountholder.entity.SettleUpProfileEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SettleUpProfileRepository : JpaRepository<SettleUpProfileEntity, UUID> {

    fun findByAccountHolderId(accountHolderId: UUID): SettleUpProfileEntity?
}
