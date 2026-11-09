package com.malai.accountholder.support

import com.malai.accountholder.entity.*
import com.malai.accountholder.repository.*
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Domain fixture interface for the account-holder service. Mixed into [com.malai.accountholder.acceptance.AccountHolderAcceptanceTest]
 * so every test inherits both:
 *
 * - `aXxxExists(...)` builders that persist domain entities directly via the running app's
 *   repository beans (resolved via [WithApplicationContext.getBean]).
 * - `theXxxInTheDatabase[Has|Have|DoesNotExist...](...)` collectors used in `then`/`and` blocks
 *   to assert on persisted state.
 */
interface WithAccountHolders : WithApplicationContext {

    fun accountHolderRepository(): AccountHolderRepository = getBean(AccountHolderRepository::class.java)
    fun individualRepository(): IndividualRepository = getBean(IndividualRepository::class.java)
    fun businessRepository(): BusinessRepository = getBean(BusinessRepository::class.java)
    fun businessAddressRepository(): BusinessAddressRepository = getBean(BusinessAddressRepository::class.java)
    fun soleTraderRepository(): SoleTraderRepository = getBean(SoleTraderRepository::class.java)
    fun addressRepository(): AddressRepository = getBean(AddressRepository::class.java)
    fun profileImageRepository(): ProfileImageRepository = getBean(ProfileImageRepository::class.java)
    fun settleUpProfileRepository(): SettleUpProfileRepository = getBean(SettleUpProfileRepository::class.java)

    // -------- builders --------

    fun anAccountHolderExists(
        accountHolderId: UUID,
        accountHolderType: AccountHolderType,
        userId: UUID = UUID.randomUUID(),
        createdAt: Instant = Instant.now(),
        status: OnboardingStatus = ACTIVE,
    ) = CallableAction {
        accountHolderRepository().save(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = userId,
                accountHolderType = accountHolderType,
                createdAt = createdAt,
                status = status,
            )
        )
    }

    fun anIndividualExists(
        individualId: UUID,
        accountHolderId: UUID,
        title: String? = "Mr",
        firstName: String = "John",
        lastName: String = "Doe",
        dateOfBirth: LocalDate = LocalDate.parse("1990-01-01"),
        email: String = "john.doe@example.com",
        phone: String = "+447000000000",
        status: OnboardingStatus = ACTIVE,
    ) = CallableAction {
        individualRepository().save(
            IndividualEntity(
                individualId = individualId,
                accountHolderId = accountHolderId,
                title = title,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                email = email,
                phone = phone,
                status = status,
            )
        )
    }

    fun aBusinessExists(
        businessId: UUID,
        accountHolderId: UUID,
        companyName: String = "Acme Ltd",
        companyType: String? = "ltd",
        companyCategory: String? = "TECHNOLOGY",
        companySubCategory: String? = "SOFTWARE",
        companyRegistrationNumber: String? = "12345678",
        email: String = "info@acme.com",
        phone: String = "+442000000000",
        status: OnboardingStatus = ACTIVE,
    ) = CallableAction {
        businessRepository().save(
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

    fun aBusinessAddressExists(
        addressId: UUID,
        businessId: UUID,
        addressType: AddressType,
        line1: String = "1 Some Street",
        line2: String? = null,
        line3: String? = null,
        postTown: String = "London",
        postCode: String = "SW1A 1AA",
        countryCode: String = "GB"
    ) = CallableAction {
        businessAddressRepository().save(
            BusinessAddressEntity(
                addressId = addressId,
                businessId = businessId,
                addressType = addressType,
                line1 = line1,
                line2 = line2,
                line3 = line3,
                postTown = postTown,
                postCode = postCode,
                countryCode = countryCode
            )
        )
    }

    fun aSoleTraderExists(
        soleTraderId: UUID,
        accountHolderId: UUID,
        tradingAsName: String = "Joe's Plumbing",
        businessCategory: String? = "HOME_AND_GARDEN",
        businessSubCategory: String? = "PLUMBING",
        status: OnboardingStatus = ACTIVE,
    ) = CallableAction {
        soleTraderRepository().save(
            SoleTraderEntity(
                soleTraderId = soleTraderId,
                accountHolderId = accountHolderId,
                tradingAsName = tradingAsName,
                businessCategory = businessCategory,
                businessSubCategory = businessSubCategory,
                status = status,
            )
        )
    }

    fun anAddressExists(
        addressId: UUID,
        accountHolderId: UUID,
        line1: String = "1 Some Street",
        line2: String? = null,
        line3: String? = null,
        postTown: String = "London",
        postCode: String = "SW1A 1AA",
        countryCode: String = "GB",
        udprn: String? = null,
        umprn: String? = null,
        fromDate: LocalDate = LocalDate.parse("2024-01-01"),
        isCurrent: Boolean = true
    ) = CallableAction {
        addressRepository().save(
            AddressEntity(
                addressId = addressId,
                accountHolderId = accountHolderId,
                line1 = line1,
                line2 = line2,
                line3 = line3,
                postTown = postTown,
                postCode = postCode,
                countryCode = countryCode,
                udprn = udprn,
                umprn = umprn,
                fromDate = fromDate,
                isCurrent = isCurrent
            )
        )
    }

    fun aProfileImageExists(
        profileImageId: UUID,
        accountHolderId: UUID,
        contentType: String = "image/png",
        imageData: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    ) = CallableAction {
        profileImageRepository().save(
            ProfileImageEntity(
                profileImageId = profileImageId,
                accountHolderId = accountHolderId,
                contentType = contentType,
                imageData = imageData
            )
        )
    }

    fun aSettleUpProfileExists(
        settleUpProfileId: UUID,
        accountHolderId: UUID,
        settleUpLink: String = "https://settleup.malaibank.com/some-user",
        status: SettleUpProfileStatus = SettleUpProfileStatus.ACTIVE
    ) = CallableAction {
        settleUpProfileRepository().save(
            SettleUpProfileEntity(
                settleUpProfileId = settleUpProfileId,
                accountHolderId = accountHolderId,
                settleUpLink = settleUpLink,
                status = status
            )
        )
    }

    // -------- state collectors / assertions --------

    fun theAccountHolderInTheDatabase(accountHolderId: UUID) = StateCollector<AccountHolderEntity> { _ ->
        accountHolderRepository().findById(accountHolderId).orElseThrow {
            AssertionError("AccountHolder with ID $accountHolderId not found in database")
        }
    }

    fun theIndividualInTheDatabase(individualId: UUID) = StateCollector<IndividualEntity> { _ ->
        individualRepository().findById(individualId).orElseThrow {
            AssertionError("Individual with ID $individualId not found in database")
        }
    }

    fun theProfileImageInTheDatabaseHas(
        accountHolderId: UUID,
        vararg assertions: (ProfileImageEntity) -> Unit
    ) = StateCollector<ProfileImageEntity> { _ ->
        val profileImage = profileImageRepository().findByAccountHolderId(accountHolderId)
            ?: throw AssertionError("Profile image for account-holder $accountHolderId not found in database")
        assertions.forEach { it(profileImage) }
        profileImage
    }

    fun theProfileImageDoesNotExistInTheDatabase(accountHolderId: UUID) = StateCollector<Unit> { _ ->
        val profileImage = profileImageRepository().findByAccountHolderId(accountHolderId)
        assertThat(profileImage)
            .withFailMessage { "Expected no profile image for account-holder $accountHolderId, but found one" }
            .isNull()
    }

    fun theCurrentAddressInTheDatabaseHas(
        accountHolderId: UUID,
        vararg assertions: (AddressEntity) -> Unit
    ) = StateCollector<AddressEntity> { _ ->
        val address = addressRepository().findByAccountHolderIdAndIsCurrent(accountHolderId, true)
            ?: throw AssertionError("Current address for account-holder $accountHolderId not found in database")
        assertions.forEach { it(address) }
        address
    }

    fun thePreviousAddressesInTheDatabaseHave(
        accountHolderId: UUID,
        vararg assertions: (List<AddressEntity>) -> Unit
    ) = StateCollector<List<AddressEntity>> { _ ->
        val previous = addressRepository().findByAccountHolderIdAndIsCurrentFalseOrderByFromDateDesc(accountHolderId)
        assertions.forEach { it(previous) }
        previous
    }
}
