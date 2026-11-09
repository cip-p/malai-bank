package com.malai.accounts.service

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.accounts.exception.InternalAccountNotFoundException
import com.malai.accounts.model.InternalAccountResponse
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service-to-service account lookups: how payments-service resolves a payer (by id, scoped to the
 * authenticated account holder) and a beneficiary (by sort-code + account-number) without reaching
 * into account-service's repositories. The internet-facing surface stays in [AccountsService].
 */
@Service
class InternalAccountsService(
    private val accountRepository: AccountRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
) {

    fun getAccount(accountId: UUID, accountHolderId: UUID): InternalAccountResponse =
        accountRepository.findByAccountIdAndAccountHolderId(accountId, accountHolderId)
            ?.let { toResponse(it) }
            ?: throw InternalAccountNotFoundException()

    fun resolveByIdentifier(sortCode: String, accountNumber: String): InternalAccountResponse {
        val identifier = accountIdentifierRepository
            .findByIdentifierTypeAndBankIdentifierAndAccountIdentifier(SORT_CODE, sortCode, accountNumber)
            ?: throw InternalAccountNotFoundException()
        val account = accountRepository.findById(identifier.accountId).orElseThrow { InternalAccountNotFoundException() }
        return toResponse(account)
    }

    private fun toResponse(entity: AccountEntity): InternalAccountResponse {
        val identifier = accountIdentifierRepository.findByAccountId(entity.accountId)
            .firstOrNull { it.identifierType == SORT_CODE }
            ?: throw InternalAccountNotFoundException()
        return InternalAccountResponse(
            accountId = entity.accountId,
            accountHolderId = entity.accountHolderId,
            currency = entity.currency,
            sortCode = identifier.bankIdentifier,
            accountNumber = identifier.accountIdentifier,
            name = entity.name,
        )
    }
}
