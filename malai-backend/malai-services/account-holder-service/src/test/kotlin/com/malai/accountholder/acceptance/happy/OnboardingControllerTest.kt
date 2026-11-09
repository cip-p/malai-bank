package com.malai.accountholder.acceptance.happy

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.AddressEntity
import com.malai.accountholder.entity.IndividualEntity
import com.malai.client.malai.accountholder.builders.request.CreateOnboardingAccountHolderRequestBuilder.Companion.aDefaultCreateOnboardingAccountHolderRequest
import com.malai.client.malai.accountholder.builders.request.CreateOnboardingAddressRequestBuilder.Companion.aDefaultCreateOnboardingAddressRequest
import com.malai.client.malai.accountholder.builders.request.CreateOnboardingIndividualRequestBuilder.Companion.aDefaultCreateOnboardingIndividualRequest
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import java.time.Instant
import java.time.LocalDate

class OnboardingControllerTest : AccountHolderAcceptanceTest() {

    @Test
    fun createOnboardingAccountHolder() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theNextGeneratedUuidWillBe(someAccountHolderUid()))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders")
                .body(aDefaultCreateOnboardingAccountHolderRequest()
                    .withUserId(someUserId()))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "accountHolderId" To someAccountHolderUid().toString()
                noExtraProps()
            }
        }

        and(theAccountHolderInTheDatabase(someAccountHolderUid())) {
            this shouldBe AccountHolderEntity(
                accountHolderId = someAccountHolderUid(),
                userId = someUserId(),
                accountHolderType = INDIVIDUAL,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                status = PENDING,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ACCOUNT_HOLDER_ONBOARDED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun createOnboardingIndividual() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(theNextGeneratedUuidWillBe(someIndividualUid()))

        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders/${someAccountHolderUid()}/individual")
                .body(aDefaultCreateOnboardingIndividualRequest()
                    .withFirstName("Alice")
                    .withLastName("Smith")
                    .withDateOfBirth("1990-04-12")
                    .withEmail("alice@example.com"))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theIndividualInTheDatabase(someIndividualUid())) {
            this shouldBe IndividualEntity(
                individualId = someIndividualUid(),
                accountHolderId = someAccountHolderUid(),
                title = null,
                firstName = "Alice",
                lastName = "Smith",
                dateOfBirth = LocalDate.parse("1990-04-12"),
                email = "alice@example.com",
                phone = "",
                status = PENDING,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "INDIVIDUAL_ONBOARDED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["actor_id"] shouldBe null
            this[0]["resource_type"] shouldBe "Individual"
            this[0]["resource_id"] shouldBe someIndividualUid().toString()
        }
    }

    @Test
    fun createOnboardingAddress() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(theNextGeneratedUuidWillBe(someAddressUid()))
        and(nowIs("2026-04-29T12:00:00Z"))

        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders/${someAccountHolderUid()}/address")
                .body(aDefaultCreateOnboardingAddressRequest()
                    .withLine1("10 Downing Street")
                    .withLine2(null)
                    .withCity("London")
                    .withPostcode("SW1A 2AA")
                    .withCountry("GB"))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theCurrentAddressInTheDatabaseHas(someAccountHolderUid())) {
            this shouldBeEqualUsingFields AddressEntity(
                addressId = someAddressUid(),
                accountHolderId = someAccountHolderUid(),
                line1 = "10 Downing Street",
                line2 = null,
                line3 = null,
                postTown = "London",
                postCode = "SW1A 2AA",
                countryCode = "GB",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.parse("2026-04-29"),
                isCurrent = true,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ADDRESS_ONBOARDED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["actor_id"] shouldBe null
            this[0]["resource_type"] shouldBe "AccountHolder"
            this[0]["resource_id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun activateOnboardingAccountHolderByUserId() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL, userId = someUserId(), status = PENDING))
        and(anIndividualExists(someIndividualUid(), someAccountHolderUid(), status = PENDING))

        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders/activate/${someUserId()}")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theAccountHolderInTheDatabase(someAccountHolderUid())) {
            status shouldBe ACTIVE
        }

        and(theIndividualInTheDatabase(someIndividualUid())) {
            status shouldBe ACTIVE
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ACCOUNT_HOLDERS_ACTIVATED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "User"
            this[0]["resource_id"] shouldBe someUserId().toString()
        }
    }
}
