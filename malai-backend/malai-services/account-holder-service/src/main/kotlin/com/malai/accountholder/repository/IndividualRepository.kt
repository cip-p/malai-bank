package com.malai.accountholder.repository

import com.malai.accountholder.entity.IndividualEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface IndividualRepository : JpaRepository<IndividualEntity, UUID> {

    fun findByAccountHolderId(accountHolderId: UUID): List<IndividualEntity>
}
