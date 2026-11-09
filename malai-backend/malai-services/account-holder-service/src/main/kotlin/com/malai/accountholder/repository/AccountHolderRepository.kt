package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.*

interface AccountHolderRepository : JpaRepository<AccountHolderEntity, UUID> {

    fun findByUserId(userId: UUID): List<AccountHolderEntity>

    fun countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from: Instant, until: Instant): Long
}
