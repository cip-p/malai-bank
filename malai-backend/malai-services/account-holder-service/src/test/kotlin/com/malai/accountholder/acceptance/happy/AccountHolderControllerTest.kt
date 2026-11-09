package com.malai.accountholder.acceptance.happy

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.*
import com.malai.accountholder.entity.AddressType
import com.malai.accountholder.entity.IndividualEntity
import com.malai.client.malai.accountholder.builders.request.CreateAccountHolderRequestBuilder.Companion.aCreateAccountHolderRequest
import com.malai.client.malai.accountholder.builders.request.CreateIndividualRequestBuilder.Companion.aCreateIndividualRequest
import com.malai.client.malai.accountholder.builders.request.UpdateAddressRequestBuilder.Companion.anUpdateAddressRequest
import com.malai.client.malai.accountholder.builders.request.UpdateEmailRequestBuilder.Companion.anUpdateEmailRequest
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Nothing.nothing
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate

class AccountHolderControllerTest : AccountHolderAcceptanceTest() {

    @Test
    fun getAccountHolder() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolderUid" To someAccountHolderUid().toString()
                "accountHolderType" To "INDIVIDUAL"
                noExtraProps()
            }
        }
    }

    @Test
    fun createAccountHolder() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theNextGeneratedUuidWillBe(someAccountHolderUid()))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            accountHolderServiceReceives(
                post()
                    .url("/api/v1/account-holder")
                    .body(
                        aCreateAccountHolderRequest()
                            .withAccountHolderType("INDIVIDUAL")
                    )
                    .withCustomerJwtTokenForUserId(someUserId())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolderUid" To someAccountHolderUid().toString()
                "accountHolderType" To "INDIVIDUAL"
                noExtraProps()
            }
        }

        and(theAccountHolderInTheDatabase(someAccountHolderUid())) {
            this shouldBeEqualUsingFields AccountHolderEntity(
                accountHolderId = someAccountHolderUid(),
                userId = someUserId(),
                accountHolderType = INDIVIDUAL,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                status = ACTIVE,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ACCOUNT_HOLDER_CREATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun getAccountHolderName() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(anIndividualExists(someIndividualUid(), someAccountHolderUid(), firstName = "John", lastName = "Doe"))

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/name")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolderName" To "John Doe"
                noExtraProps()
            }
        }
    }

    @Test
    fun getIndividual() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(
            anIndividualExists(
                someIndividualUid(), someAccountHolderUid(),
                title = "Mr",
                firstName = "John",
                lastName = "Doe",
                dateOfBirth = LocalDate.parse("1990-01-15"),
                email = "john.doe@example.com",
                phone = "+447123456789"
            )
        )

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/individual")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "title" To "Mr"
                "firstName" To "John"
                "lastName" To "Doe"
                "dateOfBirth" To "1990-01-15"
                "email" To "john.doe@example.com"
                "phone" To "+447123456789"
                noExtraProps()
            }
        }
    }

    @Test
    fun createIndividual() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(theNextGeneratedUuidWillBe(someIndividualUid()))

        whenever(
            accountHolderServiceReceives(
                post()
                    .url("/api/v1/account-holder/individual")
                    .body(
                        aCreateIndividualRequest()
                            .withTitle("Mr")
                            .withFirstName("John")
                            .withLastName("Doe")
                            .withDateOfBirth("1990-01-15")
                            .withEmail("john.doe@example.com")
                            .withPhone("+447123456789")
                    )
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "title" To "Mr"
                "firstName" To "John"
                "lastName" To "Doe"
                "dateOfBirth" To "1990-01-15"
                "email" To "john.doe@example.com"
                "phone" To "+447123456789"
                noExtraProps()
            }
        }

        and(theIndividualInTheDatabase(someIndividualUid())) {
            this shouldBeEqualUsingFields IndividualEntity(
                individualId = someIndividualUid(),
                accountHolderId = someAccountHolderUid(),
                title = "Mr",
                firstName = "John",
                lastName = "Doe",
                dateOfBirth = LocalDate.parse("1990-01-15"),
                email = "john.doe@example.com",
                phone = "+447123456789",
                status = ACTIVE,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "INDIVIDUAL_CREATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe null
            this[0]["resource_type"] shouldBe "Individual"
            this[0]["resource_id"] shouldBe someIndividualUid().toString()
        }
    }

    @Test
    fun updateEmail() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(anIndividualExists(someIndividualUid(), someAccountHolderUid(), email = "old@example.com"))

        whenever(
            accountHolderServiceReceives(
                put()
                    .url("/api/v1/account-holder/individual/email")
                    .body(
                        anUpdateEmailRequest()
                            .withEmail("new@example.com")
                    )
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }

        and(theIndividualInTheDatabase(someIndividualUid())) {
            email shouldBe "new@example.com"
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "INDIVIDUAL_EMAIL_UPDATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe null
            this[0]["resource_type"] shouldBe "Individual"
            this[0]["resource_id"] shouldBe someIndividualUid().toString()
        }
    }

    @Test
    fun getJointAccount() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), JOINT))
        and(
            anIndividualExists(
                someIndividualUid(), someAccountHolderUid(),
                title = "Mr", firstName = "John", lastName = "Doe",
                dateOfBirth = LocalDate.parse("1990-01-15"),
                email = "john@example.com", phone = "+441111111111"
            )
        )
        and(
            anIndividualExists(
                anotherIndividualUid(), someAccountHolderUid(),
                title = "Mrs", firstName = "Jane", lastName = "Doe",
                dateOfBirth = LocalDate.parse("1992-03-20"),
                email = "jane@example.com", phone = "+442222222222"
            )
        )

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/joint")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolderUid" To someAccountHolderUid().toString()
                "personOne" To json {
                    "title" To "Mr"
                    "firstName" To "John"
                    "lastName" To "Doe"
                    "dateOfBirth" To "1990-01-15"
                    "email" To "john@example.com"
                    "phone" To "+441111111111"
                    noExtraProps()
                }
                "personTwo" To json {
                    "title" To "Mrs"
                    "firstName" To "Jane"
                    "lastName" To "Doe"
                    "dateOfBirth" To "1992-03-20"
                    "email" To "jane@example.com"
                    "phone" To "+442222222222"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    @Test
    fun getBusiness() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), BUSINESS))
        and(
            aBusinessExists(
                someBusinessUid(), someAccountHolderUid(),
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

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/business")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "companyName" To "Acme Ltd"
                "companyType" To "ltd"
                "companyCategory" To "TECHNOLOGY"
                "companySubCategory" To "SOFTWARE"
                "companyRegistrationNumber" To "12345678"
                "email" To "info@acme.com"
                "phone" To "+442012345678"
                noExtraProps()
            }
        }
    }

    @Test
    fun getBusinessCorrespondenceAddress() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), BUSINESS))
        and(aBusinessExists(someBusinessUid(), someAccountHolderUid()))
        and(
            aBusinessAddressExists(
                someBusinessAddressUid(),
                someBusinessUid(),
                AddressType.CORRESPONDENCE,
                line1 = "123 High Street",
                line2 = "Floor 2",
                postTown = "London",
                postCode = "EC1A 1BB",
                countryCode = "GB"
            )
        )

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/business/correspondence-address")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "line1" To "123 High Street"
                "line2" To "Floor 2"
                "postTown" To "London"
                "postCode" To "EC1A 1BB"
                "countryCode" To "GB"
                noExtraProps()
            }
        }
    }

    @Test
    fun getBusinessRegisteredAddress() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), BUSINESS))
        and(aBusinessExists(someBusinessUid(), someAccountHolderUid()))
        and(
            aBusinessAddressExists(
                someBusinessAddressUid(), someBusinessUid(), AddressType.REGISTERED,
                line1 = "456 Main Road", postTown = "Manchester", postCode = "M1 1AA", countryCode = "GB"
            )
        )

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/business/registered-address")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "line1" To "456 Main Road"
                "postTown" To "Manchester"
                "postCode" To "M1 1AA"
                "countryCode" To "GB"
                noExtraProps()
            }
        }
    }

    @Test
    fun getSoleTrader() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), SOLE_TRADER))
        and(
            aSoleTraderExists(
                someSoleTraderUid(), someAccountHolderUid(),
                tradingAsName = "Joe's Plumbing",
                businessCategory = "HOME_AND_GARDEN",
                businessSubCategory = "PLUMBING",
                status = PENDING,
            )
        )

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/sole-trader")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "tradingAsName" To "Joe's Plumbing"
                "businessCategory" To "HOME_AND_GARDEN"
                "businessSubCategory" To "PLUMBING"
                noExtraProps()
            }
        }
    }

    @Test
    fun getAddresses() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(
            anAddressExists(
                someAddressUid(), someAccountHolderUid(),
                line1 = "123 High Street", line2 = "Floor 2", postTown = "London",
                postCode = "EC1A 1BB", countryCode = "GB", isCurrent = true,
                fromDate = LocalDate.parse("2024-01-01")
            )
        )
        and(
            anAddressExists(
                anotherAddressUid(), someAccountHolderUid(),
                line1 = "456 Old Road", postTown = "Manchester", postCode = "M1 1AA",
                countryCode = "GB", isCurrent = false,
                fromDate = LocalDate.parse("2022-06-01")
            )
        )

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/addresses")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "current" To json {
                    "line1" To "123 High Street"
                    "line2" To "Floor 2"
                    "postTown" To "London"
                    "postCode" To "EC1A 1BB"
                    "countryCode" To "GB"
                    noExtraProps()
                }
                "previous" To listOf(
                    json {
                        "line1" To "456 Old Road"
                        "postTown" To "Manchester"
                        "postCode" To "M1 1AA"
                        "countryCode" To "GB"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getAddressesWithNoCurrentAddress() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/addresses")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "current" To nothing
                "previous" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun updateAddress() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))

        whenever(
            accountHolderServiceReceives(
                post()
                    .url("/api/v1/addresses")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
                    .body(
                        anUpdateAddressRequest()
                            .withLine1("789 New Street")
                            .withPostTown("Bristol")
                            .withPostCode("BS1 1AA")
                            .withCountryCode("GB")
                            .withFrom("2025-03-01")
                    )
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }

        and(
            theCurrentAddressInTheDatabaseHas(
                someAccountHolderUid(),
                { it.line1 shouldBe "789 New Street" },
                { it.postTown shouldBe "Bristol" },
                { it.postCode shouldBe "BS1 1AA" },
                { it.countryCode shouldBe "GB" },
                { it.fromDate shouldBe LocalDate.parse("2025-03-01") },
                { it.isCurrent shouldBe true }
            )) {}

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ADDRESS_UPDATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe null
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun updateAddressDemotesExistingCurrent() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(
            anAddressExists(
                someAddressUid(), someAccountHolderUid(),
                line1 = "123 High Street", postTown = "London", postCode = "EC1A 1BB",
                countryCode = "GB", isCurrent = true,
                fromDate = LocalDate.parse("2024-01-01")
            )
        )

        whenever(
            accountHolderServiceReceives(
                post()
                    .url("/api/v1/addresses")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
                    .body(
                        anUpdateAddressRequest()
                            .withLine1("789 New Street")
                            .withPostTown("Bristol")
                            .withPostCode("BS1 1AA")
                            .withCountryCode("GB")
                            .withFrom("2025-03-01")
                    )
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }

        and(
            theCurrentAddressInTheDatabaseHas(
                someAccountHolderUid(),
                { it.line1 shouldBe "789 New Street" }
            )) {}

        and(
            thePreviousAddressesInTheDatabaseHave(
                someAccountHolderUid(),
                { it.size shouldBe 1 },
                { it[0].line1 shouldBe "123 High Street" }
            )) {}

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ADDRESS_UPDATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun getProfileImage() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(aProfileImageExists(someProfileImageUid(), someAccountHolderUid(), "image/png", someImageData))

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/profile-image")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }
    }

    @Test
    fun updateProfileImage() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))

        whenever(
            accountHolderServiceReceives(
                put()
                    .url("/api/v1/account-holder/profile-image")
                    .contentType("image/jpeg")
                    .body(someImageData)
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }

        and(
            theProfileImageInTheDatabaseHas(
                someAccountHolderUid(),
                { it.contentType shouldBe "image/jpeg" },
                { it.imageData shouldBe someImageData }
            )) {}

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PROFILE_IMAGE_UPDATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun updateProfileImageReplacesExisting() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(aProfileImageExists(someProfileImageUid(), someAccountHolderUid(), "image/png", someImageData))

        whenever(
            accountHolderServiceReceives(
                put()
                    .url("/api/v1/account-holder/profile-image")
                    .contentType("image/jpeg")
                    .body(anotherImageData)
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }

        and(
            theProfileImageInTheDatabaseHas(
                someAccountHolderUid(),
                { it.contentType shouldBe "image/jpeg" },
                { it.imageData shouldBe anotherImageData }
            )) {}

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PROFILE_IMAGE_UPDATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun deleteProfileImage() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(aProfileImageExists(someProfileImageUid(), someAccountHolderUid(), "image/png", someImageData))

        whenever(
            accountHolderServiceReceives(
                delete()
                    .url("/api/v1/account-holder/profile-image")
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }

        and(theProfileImageDoesNotExistInTheDatabase(someAccountHolderUid())) {}

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PROFILE_IMAGE_DELETED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun getAllAccountHolders() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL, userId = someUserId()))
        and(anIndividualExists(someIndividualUid(), someAccountHolderUid(), firstName = "John", lastName = "Doe"))

        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/all")
                    .withCustomerJwtTokenForUserId(someUserId())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To listOf(
                    json {
                        "accountHolderUid" To someAccountHolderUid().toString()
                        "accountHolderName" To "John Doe"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getAllAccountHoldersReturnsEmptyListWhenNoAccountHolders() = malaiAcceptanceTest {
        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/all")
                    .withCustomerJwtTokenForUserId(someUserId())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun getAllAccountHoldersReturnsEmptyListForTokenWithoutCustomerRoleOrEstate() = malaiAcceptanceTest {
        whenever(
            accountHolderServiceReceives(
                get()
                    .url("/api/v1/account-holder/all")
                    .withJwtTokenForUserId(someUserId(), listOf("admin"))
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    companion object {
        private val someImageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        private val anotherImageData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }
}
