package com.malai.accounts.service

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.accounts.model.ConfirmationOfPayeeRequest
import com.malai.accounts.model.ConfirmationOfPayeeResponse
import com.malai.accounts.model.ConfirmationOfPayeeResult
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.accounts.util.Levenshtein
import com.malai.client.malai.stub.cop.MalaiStubCopClient
import com.malai.client.malai.stub.cop.model.StubCopLookupRequest
import com.malai.client.malai.stub.cop.model.StubCopVerdict
import com.malai.commons.model.OnboardingStatus
import org.springframework.stereotype.Service

@Service
class ConfirmationOfPayeeService(
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val accountRepository: AccountRepository,
    private val stubCopClient: MalaiStubCopClient,
) {

    fun confirm(request: ConfirmationOfPayeeRequest): ConfirmationOfPayeeResponse {
        val onRecord = findEligibleAccount(request.sortCode, request.accountNumber)
            ?: return federateToStub(request)

        return verdictFor(onRecord.name, request.name)
    }

    private fun federateToStub(request: ConfirmationOfPayeeRequest): ConfirmationOfPayeeResponse {
        val stubResponse = stubCopClient.lookup(
            StubCopLookupRequest(
                sortCode = request.sortCode,
                accountNumber = request.accountNumber,
                name = request.name,
            )
        )
        return when (stubResponse.verdict) {
            StubCopVerdict.MATCH -> ConfirmationOfPayeeResponse(ConfirmationOfPayeeResult.MATCH)
            StubCopVerdict.CLOSE_MATCH -> ConfirmationOfPayeeResponse(
                ConfirmationOfPayeeResult.CLOSE_MATCH,
                onRecordName = stubResponse.onRecordName,
            )
            StubCopVerdict.NO_MATCH -> ConfirmationOfPayeeResponse(ConfirmationOfPayeeResult.NO_MATCH)
        }
    }

    private fun verdictFor(onRecordName: String, requestedName: String): ConfirmationOfPayeeResponse {
        val distance = Levenshtein.distance(onRecordName, requestedName)
        val maxLen = maxOf(onRecordName.length, requestedName.length).coerceAtLeast(1)
        val similarity = 1.0 - (distance.toDouble() / maxLen)

        return when {
            distance <= TYPO_TOLERANCE -> ConfirmationOfPayeeResponse(ConfirmationOfPayeeResult.MATCH)
            similarity >= MIN_SIMILARITY_FOR_CLOSE_MATCH ->
                ConfirmationOfPayeeResponse(ConfirmationOfPayeeResult.CLOSE_MATCH, onRecordName = onRecordName)
            else -> ConfirmationOfPayeeResponse(ConfirmationOfPayeeResult.NO_MATCH)
        }
    }

    private fun findEligibleAccount(sortCode: String, accountNumber: String): AccountEntity? {
        val identifier = accountIdentifierRepository
            .findByIdentifierTypeAndBankIdentifierAndAccountIdentifier(
                identifierType = SORT_CODE,
                bankIdentifier = sortCode,
                accountIdentifier = accountNumber,
            ) ?: return null
        val account = accountRepository.findById(identifier.accountId).orElse(null) ?: return null
        if (account.status != OnboardingStatus.ACTIVE) return null
        if (account.accountType !in ELIGIBLE_ACCOUNT_TYPES) return null
        return account
    }

    private companion object {
        const val TYPO_TOLERANCE = 2
        const val MIN_SIMILARITY_FOR_CLOSE_MATCH = 0.85

        val ELIGIBLE_ACCOUNT_TYPES = setOf(
            AccountType.PRIMARY,
            AccountType.ADDITIONAL,
            AccountType.SAVINGS,
        )
    }
}
