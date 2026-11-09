package com.malai.accountholder.repository

import com.malai.accountholder.entity.BusinessEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface BusinessRepository : JpaRepository<BusinessEntity, UUID> {

    fun findByAccountHolderId(accountHolderId: UUID): BusinessEntity?
}
