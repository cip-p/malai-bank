package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.AddressEntity
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

@SpringTest
class AddressRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var addressRepository: AddressRepository

    @BeforeEach
    fun setUp() {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = someAccountHolderUid(),
                userId = someUserId(),
                accountHolderType = INDIVIDUAL,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = PENDING,
            )
        )
    }

    @Test
    fun findByAccountHolderIdAndIsCurrentReturnsCurrentAddress() {
        persistAndFlush(
            AddressEntity(
                addressId = someAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "123 High Street",
                line2 = "Floor 2",
                line3 = null,
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB",
                udprn = "23748063",
                umprn = null,
                fromDate = LocalDate.parse("2024-01-01"),
                isCurrent = true
            )
        )

        val result = addressRepository.findByAccountHolderIdAndIsCurrent(someAccountHolderUid(), true)

        assertThat(result).usingRecursiveComparison().isEqualTo(
            AddressEntity(
                addressId = someAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "123 High Street",
                line2 = "Floor 2",
                line3 = null,
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB",
                udprn = "23748063",
                umprn = null,
                fromDate = LocalDate.parse("2024-01-01"),
                isCurrent = true
            )
        )
    }

    @Test
    fun findByAccountHolderIdAndIsCurrentReturnsNullWhenNoCurrentAddress() {
        persistAndFlush(
            AddressEntity(
                addressId = someAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "123 High Street",
                line2 = null,
                line3 = null,
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.parse("2023-01-01"),
                isCurrent = false
            )
        )

        val result = addressRepository.findByAccountHolderIdAndIsCurrent(someAccountHolderUid(), true)

        assertThat(result).isNull()
    }

    @Test
    fun findByAccountHolderIdAndIsCurrentFalseOrderByFromDateDescReturnsPreviousAddresses() {
        persistAndFlush(
            AddressEntity(
                addressId = someAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "Old Address 1",
                line2 = null,
                line3 = null,
                postTown = "Manchester",
                postCode = "M1 1AA",
                countryCode = "GB",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.parse("2022-01-01"),
                isCurrent = false
            )
        )
        persistAndFlush(
            AddressEntity(
                addressId = anotherAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "Old Address 2",
                line2 = null,
                line3 = null,
                postTown = "Birmingham",
                postCode = "B1 1AA",
                countryCode = "GB",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.parse("2023-06-01"),
                isCurrent = false
            )
        )

        val result = addressRepository.findByAccountHolderIdAndIsCurrentFalseOrderByFromDateDesc(someAccountHolderUid())

        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactly(
            AddressEntity(
                addressId = anotherAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "Old Address 2",
                line2 = null,
                line3 = null,
                postTown = "Birmingham",
                postCode = "B1 1AA",
                countryCode = "GB",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.parse("2023-06-01"),
                isCurrent = false
            ),
            AddressEntity(
                addressId = someAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "Old Address 1",
                line2 = null,
                line3 = null,
                postTown = "Manchester",
                postCode = "M1 1AA",
                countryCode = "GB",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.parse("2022-01-01"),
                isCurrent = false
            )
        )
    }

    @Test
    fun findByAccountHolderIdAndIsCurrentFalseReturnsEmptyWhenNoPreviousAddresses() {
        val result = addressRepository.findByAccountHolderIdAndIsCurrentFalseOrderByFromDateDesc(someAccountHolderUid())

        assertThat(result).isEmpty()
    }
}
