package com.malai.accounts.repository

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountIdentifierEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.IdentifierType.IBAN_BIC
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.accounts.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@SpringTest
class AccountIdentifierRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var accountIdentifierRepository: AccountIdentifierRepository

    @BeforeEach
    fun setUp() {
        persistAccount(someAccountUid())
        persistAccount(anotherAccountUid())
    }

    @Test
    fun findByAccountIdReturnsAllIdentifiersForAccount() {
        val sortCodeIdentifier = AccountIdentifierEntity(
            identifierId = someSortCodeIdentifierId(),
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = someBankIdentifier(),
            accountIdentifier = someAccountIdentifier()
        )
        val ibanBicIdentifier = AccountIdentifierEntity(
            identifierId = someIbanBicIdentifierId(),
            accountId = someAccountUid(),
            identifierType = IBAN_BIC,
            bankIdentifier = anotherBankIdentifier(),
            accountIdentifier = anotherAccountIdentifier()
        )
        persistAndFlush(sortCodeIdentifier)
        persistAndFlush(ibanBicIdentifier)

        val result = accountIdentifierRepository.findByAccountId(someAccountUid())

        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(
            AccountIdentifierEntity(
                identifierId = someSortCodeIdentifierId(),
                accountId = someAccountUid(),
                identifierType = SORT_CODE,
                bankIdentifier = someBankIdentifier(),
                accountIdentifier = someAccountIdentifier()
            ),
            AccountIdentifierEntity(
                identifierId = someIbanBicIdentifierId(),
                accountId = someAccountUid(),
                identifierType = IBAN_BIC,
                bankIdentifier = anotherBankIdentifier(),
                accountIdentifier = anotherAccountIdentifier()
            )
        )
    }

    @Test
    fun findByAccountIdExcludesIdentifiersForOtherAccounts() {
        persistAndFlush(
            AccountIdentifierEntity(
                identifierId = UUID.randomUUID(),
                accountId = anotherAccountUid(),
                identifierType = SORT_CODE,
                bankIdentifier = "608371",
                accountIdentifier = "99000002"
            )
        )

        val result = accountIdentifierRepository.findByAccountId(someAccountUid())

        assertThat(result).isEmpty()
    }

    @Test
    fun findByAccountIdReturnsEmptyListWhenNoIdentifiersExist() {
        val result = accountIdentifierRepository.findByAccountId(someAccountUid())

        assertThat(result).isEmpty()
    }

    @Test
    fun findByIdentifierTypeAndBankIdentifierAndAccountIdentifierResolvesUniqueRow() {
        persistAndFlush(
            AccountIdentifierEntity(
                identifierId = someSortCodeIdentifierId(),
                accountId = someAccountUid(),
                identifierType = SORT_CODE,
                bankIdentifier = "65-82-37",
                accountIdentifier = "99000001",
            )
        )

        val result = accountIdentifierRepository
            .findByIdentifierTypeAndBankIdentifierAndAccountIdentifier(
                identifierType = SORT_CODE,
                bankIdentifier = "65-82-37",
                accountIdentifier = "99000001",
            )

        assertThat(result).usingRecursiveComparison().isEqualTo(
            AccountIdentifierEntity(
                identifierId = someSortCodeIdentifierId(),
                accountId = someAccountUid(),
                identifierType = SORT_CODE,
                bankIdentifier = "65-82-37",
                accountIdentifier = "99000001",
            )
        )
    }

    @Test
    fun findByIdentifierTypeAndBankIdentifierAndAccountIdentifierReturnsNullWhenNoMatch() {
        persistAndFlush(
            AccountIdentifierEntity(
                identifierId = someSortCodeIdentifierId(),
                accountId = someAccountUid(),
                identifierType = SORT_CODE,
                bankIdentifier = "65-82-37",
                accountIdentifier = "99000001",
            )
        )

        val result = accountIdentifierRepository
            .findByIdentifierTypeAndBankIdentifierAndAccountIdentifier(
                identifierType = SORT_CODE,
                bankIdentifier = "65-82-37",
                accountIdentifier = "99999999",
            )

        assertThat(result).isNull()
    }

    @Test
    fun findByIdentifierTypeAndBankIdentifierAndAccountIdentifierDistinguishesIdentifierType() {
        persistAndFlush(
            AccountIdentifierEntity(
                identifierId = someSortCodeIdentifierId(),
                accountId = someAccountUid(),
                identifierType = IBAN_BIC,
                bankIdentifier = "MALAGB22XXX",
                accountIdentifier = "GB29NWBK60161331926819",
            )
        )

        val result = accountIdentifierRepository
            .findByIdentifierTypeAndBankIdentifierAndAccountIdentifier(
                identifierType = SORT_CODE,
                bankIdentifier = "MALAGB22XXX",
                accountIdentifier = "GB29NWBK60161331926819",
            )

        assertThat(result).isNull()
    }

    private fun persistAccount(id: UUID) {
        persistAndFlush(
            AccountEntity(
                accountId = id,
                accountType = AccountType.PRIMARY,
                defaultCategory = UUID.randomUUID(),
                currency = Currency.GBP,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                name = "Test Account",
                userId = UUID.randomUUID(),
                accountHolderId = UUID.randomUUID(),
                status = ACTIVE,
            )
        )
    }
}
