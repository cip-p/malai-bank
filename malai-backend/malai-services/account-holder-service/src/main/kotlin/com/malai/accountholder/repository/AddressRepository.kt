package com.malai.accountholder.repository

import com.malai.accountholder.entity.AddressEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface AddressRepository : JpaRepository<AddressEntity, UUID> {

    fun findByAccountHolderIdAndIsCurrent(accountHolderId: UUID, isCurrent: Boolean): AddressEntity?

    fun findByAccountHolderIdAndIsCurrentFalseOrderByFromDateDesc(accountHolderId: UUID): List<AddressEntity>
}
