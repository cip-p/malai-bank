package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.BUSINESS
import com.malai.accountholder.entity.BusinessEntity
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@SpringTest
class BusinessRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var businessRepository: BusinessRepository

    @BeforeEach
    fun setUp() {
        persistAccountHolder(someAccountHolderUid())
        persistAccountHolder(anotherAccountHolderUid())
    }

    @Test
    fun findByAccountHolderIdReturnsBusinessForAccountHolder() {
        persistBusiness(someBusinessUid(), someAccountHolderUid(), "Acme Ltd", "ltd", "TECHNOLOGY", "SOFTWARE", "12345678", "info@acme.com", "+442012345678", PENDING)

        val result = businessRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).usingRecursiveComparison().isEqualTo(
            BusinessEntity(
                businessId = someBusinessUid(),
                accountHolderId = someAccountHolderUid(),
                companyName = "Acme Ltd",
                companyType = "ltd",
                companyCategory = "TECHNOLOGY",
                companySubCategory = "SOFTWARE",
                companyRegistrationNumber = "12345678",
                email = "info@acme.com",
                phone = "+442012345678",
                status = PENDING,
            )
        )
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenBusinessBelongsToDifferentAccountHolder() {
        persistBusiness(someBusinessUid(), anotherAccountHolderUid(), "Other Corp", null, null, null, null, "other@corp.com", "+442098765432", ACTIVE)

        val result = businessRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenNoBusinessExists() {
        val result = businessRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    private fun persistAccountHolder(accountHolderId: UUID) {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = someUserId(),
                accountHolderType = BUSINESS,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = ACTIVE,
            )
        )
    }

    private fun persistBusiness(
        businessId: UUID,
        accountHolderId: UUID,
        companyName: String,
        companyType: String?,
        companyCategory: String?,
        companySubCategory: String?,
        companyRegistrationNumber: String?,
        email: String,
        phone: String,
        status: OnboardingStatus,
    ) {
        persistAndFlush(
            BusinessEntity(
                businessId = businessId,
                accountHolderId = accountHolderId,
                companyName = companyName,
                companyType = companyType,
                companyCategory = companyCategory,
                companySubCategory = companySubCategory,
                companyRegistrationNumber = companyRegistrationNumber,
                email = email,
                phone = phone,
                status = status,
            )
        )
    }
}
