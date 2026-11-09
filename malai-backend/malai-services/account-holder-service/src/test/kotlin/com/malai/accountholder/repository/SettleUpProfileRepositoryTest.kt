package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.SettleUpProfileEntity
import com.malai.accountholder.entity.SettleUpProfileStatus
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@SpringTest
class SettleUpProfileRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var settleUpProfileRepository: SettleUpProfileRepository

    @BeforeEach
    fun setUp() {
        persistAccountHolder(someAccountHolderUid())
        persistAccountHolder(anotherAccountHolderUid())
    }

    @Test
    fun findByAccountHolderIdReturnsProfileForAccountHolder() {
        persistSettleUpProfile(someSettleUpProfileUid(), someAccountHolderUid(), "https://settleup.malaibank.com/test.user", SettleUpProfileStatus.ACTIVE)

        val result = settleUpProfileRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).usingRecursiveComparison().isEqualTo(
            SettleUpProfileEntity(
                settleUpProfileId = someSettleUpProfileUid(),
                accountHolderId = someAccountHolderUid(),
                settleUpLink = "https://settleup.malaibank.com/test.user",
                status = SettleUpProfileStatus.ACTIVE
            )
        )
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenProfileBelongsToDifferentAccountHolder() {
        persistSettleUpProfile(someSettleUpProfileUid(), anotherAccountHolderUid(), "https://settleup.malaibank.com/other.user", SettleUpProfileStatus.ACTIVE)

        val result = settleUpProfileRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenNoProfileExists() {
        val result = settleUpProfileRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    private fun persistAccountHolder(accountHolderId: UUID) {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = someUserId(),
                accountHolderType = INDIVIDUAL,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = PENDING,
            )
        )
    }

    private fun persistSettleUpProfile(settleUpProfileId: UUID, accountHolderId: UUID, settleUpLink: String, status: SettleUpProfileStatus) {
        persistAndFlush(
            SettleUpProfileEntity(
                settleUpProfileId = settleUpProfileId,
                accountHolderId = accountHolderId,
                settleUpLink = settleUpLink,
                status = status
            )
        )
    }
}
