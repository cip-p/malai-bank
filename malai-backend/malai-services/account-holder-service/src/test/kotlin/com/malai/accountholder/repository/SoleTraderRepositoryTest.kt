package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.SOLE_TRADER
import com.malai.accountholder.entity.SoleTraderEntity
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@SpringTest
class SoleTraderRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var soleTraderRepository: SoleTraderRepository

    @BeforeEach
    fun setUp() {
        persistAccountHolder(someAccountHolderUid())
        persistAccountHolder(anotherAccountHolderUid())
    }

    @Test
    fun findByAccountHolderIdReturnsSoleTraderForAccountHolder() {
        persistSoleTrader(someSoleTraderUid(), someAccountHolderUid(), "Joe's Plumbing", "HOME_AND_GARDEN", "PLUMBING", ACTIVE)

        val result = soleTraderRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).usingRecursiveComparison().isEqualTo(
            SoleTraderEntity(
                soleTraderId = someSoleTraderUid(),
                accountHolderId = someAccountHolderUid(),
                tradingAsName = "Joe's Plumbing",
                businessCategory = "HOME_AND_GARDEN",
                businessSubCategory = "PLUMBING",
                status = ACTIVE,
            )
        )
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenSoleTraderBelongsToDifferentAccountHolder() {
        persistSoleTrader(someSoleTraderUid(), anotherAccountHolderUid(), "Other Trader", null, null, ACTIVE)

        val result = soleTraderRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenNoSoleTraderExists() {
        val result = soleTraderRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    private fun persistAccountHolder(accountHolderId: UUID) {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = someUserId(),
                accountHolderType = SOLE_TRADER,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = ACTIVE,
            )
        )
    }

    private fun persistSoleTrader(
        soleTraderId: UUID,
        accountHolderId: UUID,
        tradingAsName: String,
        businessCategory: String?,
        businessSubCategory: String?,
        status: OnboardingStatus,
    ) {
        persistAndFlush(
            SoleTraderEntity(
                soleTraderId = soleTraderId,
                accountHolderId = accountHolderId,
                tradingAsName = tradingAsName,
                businessCategory = businessCategory,
                businessSubCategory = businessSubCategory,
                status
            )
        )
    }
}
