package com.malai.api.acceptance.happy

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.IndividualEntity
import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.client.malai.accountholder.builders.request.CreateAccountHolderRequestBuilder.Companion.aCreateAccountHolderRequest
import com.malai.client.malai.accountholder.builders.request.CreateIndividualRequestBuilder.Companion.aCreateIndividualRequest
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate

class AccountHolderApiTest : MalaiApiAcceptanceTest() {

    @Test
    fun createAccountHolder() = malaiAcceptanceTest {
        given(theNextGeneratedUuidWillBe(someAccountHolderUid()))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(malaiApiReceives(
            post()
                .url("/api/v1/account-holder")
                .body(aCreateAccountHolderRequest()
                    .withAccountHolderType("INDIVIDUAL"))
                .withCustomerJwtTokenForUserId(someUserId())
        ))

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
    }

    @Test
    fun createIndividual() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL))
        and(theNextGeneratedUuidWillBe(someIndividualUid()))

        whenever(malaiApiReceives(
            post()
                .url("/api/v1/account-holder/individual")
                .body(aCreateIndividualRequest()
                    .withTitle("Mr")
                    .withFirstName("John")
                    .withLastName("Doe")
                    .withDateOfBirth("1990-01-15")
                    .withEmail("john.doe@example.com")
                    .withPhone("+447123456789"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

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
    }
}
