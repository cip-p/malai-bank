package com.malai.accounts.repository

import com.malai.accounts.entity.AccountIdentifierEntity
import com.malai.accounts.entity.IdentifierType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface AccountIdentifierRepository : JpaRepository<AccountIdentifierEntity, UUID> {
    fun findByAccountId(accountId: UUID): List<AccountIdentifierEntity>

    /**
     * Resolve an account by its public sort-code + account-number pair. Used by
     * [com.malai.accounts.service.TransfersService] to find the receiving leg of a transfer.
     * `account_identifiers (identifier_type, account_identifier)` is UNIQUE in V3, so at most
     * one row matches.
     */
    fun findByIdentifierTypeAndBankIdentifierAndAccountIdentifier(
        identifierType: IdentifierType,
        bankIdentifier: String,
        accountIdentifier: String,
    ): AccountIdentifierEntity?
}
