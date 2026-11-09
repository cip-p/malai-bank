package com.malai.accountholder.repository

import com.malai.accountholder.entity.SoleTraderEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SoleTraderRepository : JpaRepository<SoleTraderEntity, UUID> {

    fun findByAccountHolderId(accountHolderId: UUID): SoleTraderEntity?
}
