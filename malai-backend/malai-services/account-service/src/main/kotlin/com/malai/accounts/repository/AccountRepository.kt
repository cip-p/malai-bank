package com.malai.accounts.repository

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface AccountRepository : JpaRepository<AccountEntity, UUID> {
    fun existsByAccountHolderIdAndAccountType(accountHolderId: UUID, accountType: AccountType): Boolean
    fun findByAccountHolderId(accountHolderId: UUID): List<AccountEntity>
    fun findByUserId(userId: UUID): List<AccountEntity>
    fun findByAccountIdAndAccountHolderId(accountId: UUID, accountHolderId: UUID): AccountEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "SELECT a FROM AccountEntity a " +
            "WHERE a.accountId = :accountId AND a.accountHolderId = :accountHolderId",
    )
    fun lockByAccountIdAndAccountHolderId(
        @Param("accountId") accountId: UUID,
        @Param("accountHolderId") accountHolderId: UUID,
    ): AccountEntity?
}
