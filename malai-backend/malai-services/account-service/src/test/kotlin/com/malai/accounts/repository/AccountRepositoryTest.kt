package com.malai.accounts.repository

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.AccountType.*
import com.malai.accounts.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import jakarta.persistence.LockModeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@SpringTest
class AccountRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Test
    fun existsByAccountHolderIdAndAccountTypeReturnsTrueWhenAccountOfTypeExistsForAccountHolder() {
        persistAccount(UUID.randomUUID(), PRIMARY, someUserId(), someAccountHolderUid())

        val result = accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), PRIMARY)

        assertThat(result).isTrue()
    }

    @Test
    fun existsByAccountHolderIdAndAccountTypeReturnsFalseWhenNoAccountOfTypeExistsForAccountHolder() {
        persistAccount(UUID.randomUUID(), ADDITIONAL, someUserId(), someAccountHolderUid())

        val result = accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), PRIMARY)

        assertThat(result).isFalse()
    }

    @Test
    fun existsByAccountHolderIdAndAccountTypeReturnsFalseWhenNoAccountsExist() {
        val result = accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), PRIMARY)

        assertThat(result).isFalse()
    }

    @Test
    fun existsByAccountHolderIdAndAccountTypeMatchesOnlyTheRequestedType() {
        persistAccount(UUID.randomUUID(), PRIMARY, someUserId(), someAccountHolderUid())
        persistAccount(UUID.randomUUID(), ADDITIONAL, someUserId(), someAccountHolderUid())
        persistAccount(UUID.randomUUID(), SAVINGS, someUserId(), someAccountHolderUid())

        assertThat(accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), PRIMARY)).isTrue()
        assertThat(accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), ADDITIONAL)).isTrue()
        assertThat(accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), SAVINGS)).isTrue()
        assertThat(accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), LOAN)).isFalse()
        assertThat(accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), FIXED_TERM_DEPOSIT)).isFalse()
    }

    @Test
    fun existsByAccountHolderIdAndAccountTypeDoesNotMatchAccountsOfOtherAccountHolders() {
        persistAccount(UUID.randomUUID(), PRIMARY, anotherUserId(), anotherAccountHolderUid())

        val result = accountRepository.existsByAccountHolderIdAndAccountType(someAccountHolderUid(), PRIMARY)

        assertThat(result).isFalse()
    }

    @Test
    fun findByAccountHolderIdReturnsAllAccountsForAccountHolder() {
        persistAccount(someAccountUid(), PRIMARY, someUserId(), someAccountHolderUid(), Currency.GBP, someDefaultCategory(), "Current Account", ACTIVE)
        persistAccount(anotherAccountUid(), ADDITIONAL, someUserId(), someAccountHolderUid(), Currency.EUR, anotherDefaultCategory(), "Euro Account", PENDING)

        val result = accountRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(
            AccountEntity(
                accountId = someAccountUid(),
                accountType = PRIMARY,
                defaultCategory = someDefaultCategory(),
                currency = Currency.GBP,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                name = "Current Account",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = ACTIVE,
            ),
            AccountEntity(
                accountId = anotherAccountUid(),
                accountType = ADDITIONAL,
                defaultCategory = anotherDefaultCategory(),
                currency = Currency.EUR,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                name = "Euro Account",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = PENDING,
            )
        )
    }

    @Test
    fun findByAccountHolderIdExcludesAccountsOfOtherAccountHolders() {
        persistAccount(someAccountUid(), PRIMARY, someUserId(), someAccountHolderUid(), Currency.GBP, someDefaultCategory(), "My Account", PENDING)
        persistAccount(anotherAccountUid(), PRIMARY, anotherUserId(), anotherAccountHolderUid(), Currency.GBP, anotherDefaultCategory(), "Other Account", ACTIVE)

        val result = accountRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(
            AccountEntity(
                accountId = someAccountUid(),
                accountType = PRIMARY,
                defaultCategory = someDefaultCategory(),
                currency = Currency.GBP,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                name = "My Account",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = PENDING,
            )
        )
    }

    @Test
    fun findByAccountHolderIdReturnsEmptyListWhenNoAccountsExistForAccountHolder() {
        val result = accountRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isEmpty()
    }

    @Test
    fun findByAccountIdAndAccountHolderIdReturnsAccountWhenBothMatch() {
        persistAccount(someAccountUid(), PRIMARY, someUserId(), someAccountHolderUid(), Currency.GBP, someDefaultCategory(), "Current Account", PENDING)

        val result = accountRepository.findByAccountIdAndAccountHolderId(someAccountUid(), someAccountHolderUid())

        assertThat(result).usingRecursiveComparison().isEqualTo(
            AccountEntity(
                accountId = someAccountUid(),
                accountType = PRIMARY,
                defaultCategory = someDefaultCategory(),
                currency = Currency.GBP,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                name = "Current Account",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = PENDING,
            )
        )
    }

    @Test
    fun findByAccountIdAndAccountHolderIdReturnsNullWhenAccountBelongsToDifferentAccountHolder() {
        persistAccount(someAccountUid(), PRIMARY, someUserId(), someAccountHolderUid())

        val result = accountRepository.findByAccountIdAndAccountHolderId(someAccountUid(), anotherAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun findByAccountIdAndAccountHolderIdReturnsNullWhenAccountDoesNotExist() {
        val result = accountRepository.findByAccountIdAndAccountHolderId(someAccountUid(), someAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun lockByAccountIdAndAccountHolderIdReturnsAccountWhenBothMatch() {
        persistAccount(someAccountUid(), PRIMARY, someUserId(), someAccountHolderUid(), Currency.GBP, someDefaultCategory(), "Current Account", PENDING)

        val result = accountRepository.lockByAccountIdAndAccountHolderId(someAccountUid(), someAccountHolderUid())

        assertThat(result).usingRecursiveComparison().isEqualTo(
            AccountEntity(
                accountId = someAccountUid(),
                accountType = PRIMARY,
                defaultCategory = someDefaultCategory(),
                currency = Currency.GBP,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                name = "Current Account",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = PENDING,
            )
        )
    }

    @Test
    fun lockByAccountIdAndAccountHolderIdReturnsNullWhenAccountBelongsToDifferentAccountHolder() {
        persistAccount(someAccountUid(), PRIMARY, someUserId(), someAccountHolderUid())

        val result = accountRepository.lockByAccountIdAndAccountHolderId(someAccountUid(), anotherAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun lockByAccountIdAndAccountHolderIdReturnsNullWhenAccountDoesNotExist() {
        val result = accountRepository.lockByAccountIdAndAccountHolderId(someAccountUid(), someAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun lockByAccountIdAndAccountHolderIdHoldsAPessimisticLockOnReturnedEntity() {
        persistAccount(someAccountUid(), PRIMARY, someUserId(), someAccountHolderUid())

        val result = accountRepository.lockByAccountIdAndAccountHolderId(someAccountUid(), someAccountHolderUid())

        assertThat(result).isNotNull
        assertThat(entityManager().getLockMode(result!!)).isIn(
            LockModeType.PESSIMISTIC_READ,
            LockModeType.PESSIMISTIC_WRITE,
            LockModeType.PESSIMISTIC_FORCE_INCREMENT,
        )
    }

    private fun persistAccount(accountId: UUID, accountType: AccountType, userId: UUID, accountHolderId: UUID, currency: Currency = Currency.GBP, defaultCategory: UUID = UUID.randomUUID(), name: String = "Test Account", status: OnboardingStatus = ACTIVE) {
        persistAndFlush(
            AccountEntity(
                accountId = accountId,
                accountType = accountType,
                defaultCategory = defaultCategory,
                currency = currency,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                name = name,
                userId = userId,
                accountHolderId = accountHolderId,
                status = status,
            )
        )
    }
}
