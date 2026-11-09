package com.malai.auth.acceptance.happy

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.client.malai.auth.builders.request.UpdateUserRequestBuilder.Companion.aDefaultUpdateUserRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate


class UserControllerTest : AuthAcceptanceTest() {

    @Test
    fun getUser() = malaiAcceptanceTest {
        given(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
        and(cognitoGetUserReturns(someUserId(), someEmail(), "Alice Smith"))

        whenever(authServiceReceives(
            get()
                .url("/api/v1/user")
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "firstName" To "Alice"
                "lastName" To "Smith"
                "email" To someEmail()
                "dateOfBirth" To "1990-04-12"
                "roles" To listOf("CUSTOMER")
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.GetUser"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
        }
    }

    @Test
    fun getUserReturnsEveryRoleTheTokenGrants() = malaiAcceptanceTest {
        given(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
        and(cognitoGetUserReturns(someUserId(), someEmail(), "Alice Smith"))

        whenever(authServiceReceives(
            get()
                .url("/api/v1/user")
                .withJwtTokenForUserId(someUserId(), listOf("customer", "admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "firstName" To "Alice"
                "lastName" To "Smith"
                "email" To someEmail()
                "dateOfBirth" To "1990-04-12"
                "roles" To listOf("CUSTOMER", "ADMIN")
                noExtraProps()
            }
        }
    }

    @Test
    fun getUserServesAStaffOnlyTokenWithNoCustomerRole() = malaiAcceptanceTest {
        given(aUserExists(someUserId(), someEmail(), "Priya", "Sharma"))
        and(cognitoGetUserReturns(someUserId(), someEmail(), "Priya Sharma"))

        whenever(authServiceReceives(
            get()
                .url("/api/v1/user")
                .withJwtTokenForUserId(someUserId(), listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "firstName" To "Priya"
                "lastName" To "Sharma"
                "email" To someEmail()
                "dateOfBirth" To "1990-04-12"
                "roles" To listOf("ADMIN")
                noExtraProps()
            }
        }
    }

    @Test
    fun getUserSurfacesTheManagerRoleForAManagerOnlyToken() = malaiAcceptanceTest {
        given(aUserExists(someUserId(), someEmail(), "Nadia", "Okonkwo"))
        and(cognitoGetUserReturns(someUserId(), someEmail(), "Nadia Okonkwo"))

        whenever(authServiceReceives(
            get()
                .url("/api/v1/user")
                .withJwtTokenForUserId(someUserId(), listOf("manager"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "firstName" To "Nadia"
                "lastName" To "Okonkwo"
                "email" To someEmail()
                "dateOfBirth" To "1990-04-12"
                "roles" To listOf("MANAGER")
                noExtraProps()
            }
        }
    }

    @Test
    fun updateUser() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExistsConfirmed(
            someUserId(),
            someEmail(),
            "Alice",
            "Smith",
            LocalDate.parse("1990-04-12"),
            Instant.parse("2026-10-15T10:30:00Z"),
            someUserId().toString()
        ))
        and(cognitoGetUserReturns(someUserId(), someEmail(), "Alice Smith"))
        and(cognitoUpdateUserAttributesReturnsSuccessful())
        and(nowIs("2026-10-15T12:00:00Z"))

        whenever(authServiceReceives(
            put()
                .url("/api/v1/user")
                .body(aDefaultUpdateUserRequest()
                    .withFirstName("Alicia")
                    .withLastName("Smith"))
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "firstName" To "Alicia"
                "lastName" To "Smith"
                "email" To someEmail()
                "dateOfBirth" To "1990-04-12"
                "roles" To listOf("CUSTOMER")
                noExtraProps()
            }
        }

        and(theUserInTheDatabase(someUserId())) {
            this shouldBe UserEntity(
                id = someUserId(),
                email = someEmail(),
                firstName = "Alicia",
                lastName = "Smith",
                dateOfBirth = LocalDate.parse("1990-04-12"),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                createdBy = someUserId().toString(),
                modifiedAt = Instant.parse("2026-10-15T12:00:00Z"),
                modifiedBy = someUserId().toString()
            )
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.UpdateUserAttributes"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "UserAttributes" To listOf(
                    json {
                        "Name" To "name"
                        "Value" To "Alicia Smith"
                    }
                )
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "USER_PROFILE_UPDATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "User"
            this[0]["resource_id"] shouldBe someUserId().toString()
        }
    }
}
