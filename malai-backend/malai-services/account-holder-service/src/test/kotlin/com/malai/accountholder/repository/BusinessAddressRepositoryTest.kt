package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.BUSINESS
import com.malai.accountholder.entity.AddressType
import com.malai.accountholder.entity.BusinessAddressEntity
import com.malai.accountholder.entity.BusinessEntity
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@SpringTest
class BusinessAddressRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var businessAddressRepository: BusinessAddressRepository

    @BeforeEach
    fun setUp() {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = someAccountHolderUid(),
                userId = someUserId(),
                accountHolderType = BUSINESS,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = PENDING,
            )
        )
        persistAndFlush(
            BusinessEntity(
                businessId = someBusinessUid(),
                accountHolderId = someAccountHolderUid(),
                companyName = "Acme Ltd",
                companyType = null,
                companyCategory = null,
                companySubCategory = null,
                companyRegistrationNumber = null,
                email = "info@acme.com",
                phone = "+442012345678",
                status = PENDING,
            )
        )
    }

    @Test
    fun findByBusinessIdAndAddressTypeReturnsMatchingAddress() {
        persistAndFlush(
            BusinessAddressEntity(
                addressId = someBusinessAddressUid(),
                businessId = someBusinessUid(),
                addressType = AddressType.CORRESPONDENCE,
                line1 = "123 High Street",
                line2 = "Floor 2",
                line3 = null,
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB"
            )
        )

        val result = businessAddressRepository.findByBusinessIdAndAddressType(someBusinessUid(), AddressType.CORRESPONDENCE)

        assertThat(result).usingRecursiveComparison().isEqualTo(
            BusinessAddressEntity(
                addressId = someBusinessAddressUid(),
                businessId = someBusinessUid(),
                addressType = AddressType.CORRESPONDENCE,
                line1 = "123 High Street",
                line2 = "Floor 2",
                line3 = null,
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB"
            )
        )
    }

    @Test
    fun findByBusinessIdAndAddressTypeReturnsNullWhenAddressTypeDoesNotMatch() {
        persistAndFlush(
            BusinessAddressEntity(
                addressId = someBusinessAddressUid(),
                businessId = someBusinessUid(),
                addressType = AddressType.REGISTERED,
                line1 = "456 Main Road",
                line2 = null,
                line3 = null,
                postTown = "Manchester",
                postCode = "M1 1AA",
                countryCode = "GB"
            )
        )

        val result = businessAddressRepository.findByBusinessIdAndAddressType(someBusinessUid(), AddressType.CORRESPONDENCE)

        assertThat(result).isNull()
    }

    @Test
    fun findByBusinessIdAndAddressTypeReturnsNullWhenNoAddressExists() {
        val result = businessAddressRepository.findByBusinessIdAndAddressType(someBusinessUid(), AddressType.CORRESPONDENCE)

        assertThat(result).isNull()
    }

    @Test
    fun findByBusinessIdAndAddressTypeDistinguishesBetweenAddressTypes() {
        persistAndFlush(
            BusinessAddressEntity(
                addressId = someBusinessAddressUid(),
                businessId = someBusinessUid(),
                addressType = AddressType.CORRESPONDENCE,
                line1 = "123 High Street",
                line2 = null,
                line3 = null,
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB"
            )
        )
        persistAndFlush(
            BusinessAddressEntity(
                addressId = anotherBusinessAddressUid(),
                businessId = someBusinessUid(),
                addressType = AddressType.REGISTERED,
                line1 = "456 Main Road",
                line2 = null,
                line3 = null,
                postTown = "Manchester",
                postCode = "M1 1AA",
                countryCode = "GB"
            )
        )

        val correspondence = businessAddressRepository.findByBusinessIdAndAddressType(someBusinessUid(), AddressType.CORRESPONDENCE)
        val registered = businessAddressRepository.findByBusinessIdAndAddressType(someBusinessUid(), AddressType.REGISTERED)

        assertThat(correspondence).usingRecursiveComparison().isEqualTo(
            BusinessAddressEntity(
                addressId = someBusinessAddressUid(),
                businessId = someBusinessUid(),
                addressType = AddressType.CORRESPONDENCE,
                line1 = "123 High Street",
                line2 = null,
                line3 = null,
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB"
            )
        )

        assertThat(registered).usingRecursiveComparison().isEqualTo(
            BusinessAddressEntity(
                addressId = anotherBusinessAddressUid(),
                businessId = someBusinessUid(),
                addressType = AddressType.REGISTERED,
                line1 = "456 Main Road",
                line2 = null,
                line3 = null,
                postTown = "Manchester",
                postCode = "M1 1AA",
                countryCode = "GB"
            )
        )
    }
}
