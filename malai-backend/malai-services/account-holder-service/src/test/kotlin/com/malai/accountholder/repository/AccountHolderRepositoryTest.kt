package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.entity.AccountHolderType.BUSINESS
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.AccountHolderType.JOINT
import com.malai.accountholder.entity.AccountHolderType.SOLE_TRADER
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

@SpringTest
class AccountHolderRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var accountHolderRepository: AccountHolderRepository

    private val aSoleTraderAccountHolderUid = UUID.fromString("00000000-0000-4000-8000-0000000000a3")
    private val aJointAccountHolderUid = UUID.fromString("00000000-0000-4000-8000-0000000000a4")

    @Test
    fun findByUserIdReturnsAllAccountHoldersForUser() {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = someAccountHolderUid(),
                userId = someUserId(),
                accountHolderType = INDIVIDUAL,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = PENDING,
                )
        )
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = anotherAccountHolderUid(),
                userId = someUserId(),
                accountHolderType = BUSINESS,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = PENDING,
            )
        )

        val result = accountHolderRepository.findByUserId(someUserId())

        assertThat(result).hasSize(2)
        assertThat(result.map { it.accountHolderId }).containsExactlyInAnyOrder(
            someAccountHolderUid(), anotherAccountHolderUid()
        )
    }

    @Test
    fun findByUserIdExcludesOtherUsersAccountHolders() {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = someAccountHolderUid(),
                userId = anotherUserId(),
                accountHolderType = INDIVIDUAL,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = PENDING,
            )
        )

        val result = accountHolderRepository.findByUserId(someUserId())

        assertThat(result).isEmpty()
    }

    @Test
    fun findByUserIdReturnsEmptyListWhenNoAccountHoldersExist() {
        val result = accountHolderRepository.findByUserId(someUserId())

        assertThat(result).isEmpty()
    }

    @Test
    fun countByCreatedAtCountsEveryHolderTypeWithinTheWindow() {
        persistHolder(someAccountHolderUid(), INDIVIDUAL, "2026-07-20T09:00:00Z")
        persistHolder(anotherAccountHolderUid(), BUSINESS, "2026-07-20T10:00:00Z")
        persistHolder(aSoleTraderAccountHolderUid, SOLE_TRADER, "2026-07-20T11:00:00Z")
        persistHolder(aJointAccountHolderUid, JOINT, "2026-07-20T12:00:00Z")

        val result = accountHolderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant.parse("2026-07-19T23:00:00Z"),
            Instant.parse("2026-07-20T23:00:00Z"),
        )

        assertThat(result).isEqualTo(4)
    }

    @Test
    fun countByCreatedAtIsHalfOpenSoTheLowerBoundCountsAndTheUpperBoundDoesNot() {
        persistHolder(someAccountHolderUid(), INDIVIDUAL, "2026-07-19T23:00:00Z")
        persistHolder(anotherAccountHolderUid(), INDIVIDUAL, "2026-07-20T23:00:00Z")

        val result = accountHolderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant.parse("2026-07-19T23:00:00Z"),
            Instant.parse("2026-07-20T23:00:00Z"),
        )

        assertThat(result).isEqualTo(1)
    }

    @Test
    fun countByCreatedAtExcludesHoldersCreatedBeforeTheWindow() {
        persistHolder(someAccountHolderUid(), INDIVIDUAL, "2026-07-19T22:59:59Z")

        val result = accountHolderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant.parse("2026-07-19T23:00:00Z"),
            Instant.parse("2026-07-20T23:00:00Z"),
        )

        assertThat(result).isZero()
    }

    @Test
    fun countByCreatedAtIsZeroWhenNoAccountHoldersExist() {
        val result = accountHolderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant.parse("2026-07-19T23:00:00Z"),
            Instant.parse("2026-07-20T23:00:00Z"),
        )

        assertThat(result).isZero()
    }

    private fun persistHolder(accountHolderId: UUID, accountHolderType: AccountHolderType, createdAt: String) =
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = someUserId(),
                accountHolderType = accountHolderType,
                createdAt = Instant.parse(createdAt),
                status = PENDING,
            )
        )
}
