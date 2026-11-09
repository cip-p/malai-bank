package com.malai.auth.acceptance.sad

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.client.malai.auth.builders.request.LoginRequestBuilder.Companion.aDefaultLoginRequest
import com.malai.client.malai.auth.builders.request.RefreshTokenRequestBuilder.Companion.aDefaultRefreshTokenRequest
import com.malai.client.malai.auth.builders.request.RegisterUserRequestBuilder.Companion.aDefaultRegisterUserRequest
import com.malai.client.malai.auth.builders.request.VerifyCodeRequestBuilder.Companion.aDefaultVerifyCodeRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.*
import java.time.Instant
import java.time.LocalDate


class AuthControllerTest : AuthAcceptanceTest() {

    @Test
    fun registerReturns400WhenEmailIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/register")
                .body(aDefaultRegisterUserRequest()
                    .withEmail(null))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun registerReturns400WhenFirstNameIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/register")
                .body(aDefaultRegisterUserRequest()
                    .withFirstName(null))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun registerReturns400WhenLastNameIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/register")
                .body(aDefaultRegisterUserRequest()
                    .withLastName(null))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun registerReturns500WhenAdminAddUserToGroupFails() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(cognitoRegisterReturnsSuccessfulForUserId(someUserId()))
        and(cognitoAdminAddUserToGroupReturnsServerError())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/register")
                .body(aDefaultRegisterUserRequest()
                    .withEmail(someEmail()))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe INTERNAL_SERVER_ERROR
            body containsProp json {
                "message" To "5975"
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.AdminAddUserToGroup"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
        }

        and(authServiceMadeTheFollowingHttpRequests {
            url.contains("/api/v1/internal/onboarding/")
        }) {
            shouldBeEmpty()
        }
    }

    @Test
    fun verifyCodeReturns401WithTheGenericErrorWhenTheCodeIsInvalid() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExistsNotConfirmed(
            someUserId(),
            someEmail(),
            "Alice",
            "Smith",
            LocalDate.parse("1985-03-02"),
            Instant.parse("2026-10-15T10:30:00Z"),
            someUserId().toString()
        ))
        and(cognitoConfirmSignUpReturnsCodeMismatch())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("a-wrong-code"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5974"
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "VERIFICATION_FAILED"
            this.single()["actor_type"] shouldBe "SYSTEM"
            this.single()["actor_id"] shouldBe null
            (this.single()["after_state"] as String) shouldContain "\"attemptedEmail\""
            (this.single()["after_state"] as String) shouldContain "\"${someEmail()}\""
        }
    }

    @Test
    fun verifyCodeReturns401WithTheGenericErrorWhenTheEmailIsUnknown() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail("nobody@example.com")
                    .withConfirmationCode("some-code"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5974"
                noExtraProps()
            }
        }
    }

    @Test
    fun verifyCodeReturns400WhenEmailIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(null))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun verifyCodeReturns401WithTheGenericErrorWhenTheLoginCodeIsWrong() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
        and(cognitoAnswerLoginChallengeReturnsCodeMismatch())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .header("Cookie", "challenge_session=some-login-challenge-session")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("a-wrong-code"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5974"
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "VERIFICATION_FAILED"
            this.single()["actor_type"] shouldBe "SYSTEM"
            this.single()["actor_id"] shouldBe null
            (this.single()["after_state"] as String) shouldContain "\"attemptedEmail\""
            (this.single()["after_state"] as String) shouldContain "\"${someEmail()}\""
        }
    }

    @Test
    fun verifyCodeReturns401WithTheGenericErrorWhenTheChallengeSessionIsMissing() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("some-login-code"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5974"
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequests {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.RespondToAuthChallenge"
        }) {
            shouldBeEmpty()
        }
    }

    @Test
    fun loginReturns400WhenUsernameIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/login")
                .body(aDefaultLoginRequest()
                    .withEmail(null))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun refreshTokenReturns401WhenUserIdIsAbsentAndNoAccessCookieToDeriveItFrom() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/refresh-token")
                .body(aDefaultRefreshTokenRequest()
                    .withUserId(null))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5980"
                noExtraProps()
            }
        }
    }

    @Test
    fun refreshTokenReturns401WhenRefreshTokenIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/refresh-token")
                .body(aDefaultRefreshTokenRequest()
                    .withRefreshToken(null))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5981"
                noExtraProps()
            }
        }
    }

    @Test
    fun refreshTokenReturns401WhenRequestHasNoBodyAtAll() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/refresh-token")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5981"
                noExtraProps()
            }
        }
    }

    @Test
    fun signOutReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/signout")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }
}
