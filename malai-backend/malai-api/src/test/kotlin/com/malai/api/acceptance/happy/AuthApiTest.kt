package com.malai.api.acceptance.happy

import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.client.malai.auth.builders.request.LoginRequestBuilder.Companion.aDefaultLoginRequest
import com.malai.client.malai.auth.builders.request.RegisterUserRequestBuilder.Companion.aDefaultRegisterUserRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate

class AuthApiTest : MalaiApiAcceptanceTest() {

    @Test
    fun registerUser() = malaiAcceptanceTest {
        given(cognitoRegisterReturnsSuccessfulForUserId(someUserId()))
        and(cognitoAdminAddUserToGroupReturnsSuccessful())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(createOnboardingAccountHolderReturnsAccountHolderId(someAccountHolderUid()))
        and(createOnboardingIndividualReturnsSuccess(someAccountHolderUid()))
        and(createOnboardingAddressReturnsSuccess(someAccountHolderUid()))
        and(createOnboardingAccountReturnsAccountId(someAccountUid()))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(generatedDetailsAre(
            dateOfBirth = LocalDate.parse("1985-03-02"),
            line1 = "14 Rosebery Avenue",
            line2 = null,
            city = "London",
            postcode = "EC1R 4TD",
            country = "GB",
        ))

        whenever(malaiApiReceives(
            post()
                .url("/api/v1/auth/register")
                .body(aDefaultRegisterUserRequest()
                    .withFirstName("Alice")
                    .withLastName("Smith")
                    .withEmail(someEmail())
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        and(theUserInTheDatabase(someUserId())) {
            this shouldBeEqualUsingFields UserEntity(
                id = someUserId(),
                email = someEmail(),
                firstName = "Alice",
                lastName = "Smith",
                dateOfBirth = LocalDate.parse("1985-03-02"),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = false,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                createdBy = someUserId().toString()
            )
        }

        and(malaiApiMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.SignUp"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
            }
            body!! shouldNotContain "Password"
        }
    }

    @Test
    fun login() = malaiAcceptanceTest {
        given(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
        and(cognitoStartLoginChallengeReturnsSession("some-login-challenge-session"))

        whenever(malaiApiReceives(
            post()
                .url("/api/v1/auth/login")
                .body(aDefaultLoginRequest()
                    .withEmail(someEmail())
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        and(malaiApiMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "AuthFlow" To "USER_AUTH"
                "AuthParameters" To json {
                    "USERNAME" To someEmail()
                    "PREFERRED_CHALLENGE" To "EMAIL_OTP"
                }
            }
        }
    }
}
