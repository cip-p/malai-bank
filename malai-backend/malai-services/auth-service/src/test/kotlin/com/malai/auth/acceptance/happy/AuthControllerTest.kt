package com.malai.auth.acceptance.happy

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.client.malai.auth.builders.request.LoginRequestBuilder.Companion.aDefaultLoginRequest
import com.malai.client.malai.auth.builders.request.RefreshTokenRequestBuilder.Companion.aDefaultRefreshTokenRequest
import com.malai.client.malai.auth.builders.request.RegisterUserRequestBuilder.Companion.aDefaultRegisterUserRequest
import com.malai.client.malai.auth.builders.request.VerifyCodeRequestBuilder.Companion.aDefaultVerifyCodeRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate


class AuthControllerTest : AuthAcceptanceTest() {

    @Test
    fun registerUser() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(cognitoRegisterReturnsSuccessfulForUserId(someUserId()))
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

        whenever(authServiceReceives(
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

        and(theLastResponseHas()) {
            setCookies.challenge()?.copy(value = "fabricated") shouldBe
                SessionCookieMatcher(value = "fabricated", maxAge = "600")
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

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.SignUp"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
            }
            body!! shouldNotContain "Password"
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.AdminAddUserToGroup" &&
                body!!.contains("customer")
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
                "GroupName" To "customer"
                "UserPoolId" To "test-user-pool-id"
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.AdminAddUserToGroup" &&
                body!!.contains("admin")
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
                "GroupName" To "admin"
                "UserPoolId" To "test-user-pool-id"
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.AdminAddUserToGroup" &&
                body!!.contains("manager")
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
                "GroupName" To "manager"
                "UserPoolId" To "test-user-pool-id"
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            url.endsWith("/api/v1/internal/onboarding/account-holders")
        }) {
            method shouldBe POST
            body containsProp json {
                "userId" To someUserId().toString()
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            url.endsWith("/api/v1/internal/onboarding/account-holders/${someAccountHolderUid()}/individual")
        }) {
            method shouldBe POST
            body containsProp json {
                "firstName" To "Alice"
                "lastName" To "Smith"
                "dateOfBirth" To "1985-03-02"
                "email" To someEmail()
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            url.endsWith("/api/v1/internal/onboarding/account-holders/${someAccountHolderUid()}/address")
        }) {
            method shouldBe POST
            body containsProp json {
                "line1" To "14 Rosebery Avenue"
                "line2" To null
                "city" To "London"
                "postcode" To "EC1R 4TD"
                "country" To "GB"
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            url.endsWith("/api/v1/internal/onboarding/accounts")
        }) {
            method shouldBe POST
            body containsProp json {
                "userId" To someUserId().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "currency" To "GBP"
                "name" To "Personal"
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "REGISTERED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "User"
            this[0]["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun registerWithATakenConfirmedEmailSilentlyStartsALoginChallenge() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExistsConfirmed(
            someUserId(),
            someEmail(),
            "Alice",
            "Smith",
            LocalDate.parse("1985-03-02"),
            Instant.parse("2026-10-15T10:30:00Z"),
            someUserId().toString()
        ))
        and(cognitoStartLoginChallengeReturnsSession("some-login-challenge-session"))

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/register")
                .body(aDefaultRegisterUserRequest()
                    .withFirstName("Mallory")
                    .withLastName("Hacker")
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

        and(theLastResponseHas()) {
            setCookies.challenge() shouldBe SessionCookieMatcher(value = "some-login-challenge-session", maxAge = "600")
        }

        and(theUserInTheDatabase(someUserId())) {
            this shouldBeEqualUsingFields UserEntity(
                id = someUserId(),
                email = someEmail(),
                firstName = "Alice",
                lastName = "Smith",
                dateOfBirth = LocalDate.parse("1985-03-02"),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                createdBy = someUserId().toString()
            )
        }

        and(authServiceMadeTheFollowingHttpRequest {
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

        and(authServiceMadeTheFollowingHttpRequests {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.SignUp"
        }) {
            shouldBeEmpty()
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "LOGIN_CHALLENGE_STARTED"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun registerWithAnUnconfirmedEmailReSendsTheConfirmationCode() = malaiAcceptanceTest {
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
        and(cognitoResendConfirmationCodeReturnsSuccessful())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/register")
                .body(aDefaultRegisterUserRequest()
                    .withFirstName("Mallory")
                    .withLastName("Hacker")
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

        and(theLastResponseHas()) {
            setCookies.challenge()?.copy(value = "fabricated") shouldBe
                SessionCookieMatcher(value = "fabricated", maxAge = "600")
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

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.ResendConfirmationCode"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
            }
        }

        and(authServiceMadeTheFollowingHttpRequests {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.SignUp"
        }) {
            shouldBeEmpty()
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "CODE_RESENT"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun loginWithAnUnconfirmedEmailReSendsTheConfirmationCode() = malaiAcceptanceTest {
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
        and(cognitoResendConfirmationCodeReturnsSuccessful())

        whenever(authServiceReceives(
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

        and(theLastResponseHas()) {
            setCookies.challenge()?.copy(value = "fabricated") shouldBe
                SessionCookieMatcher(value = "fabricated", maxAge = "600")
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.ResendConfirmationCode"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
            }
        }

        and(authServiceMadeTheFollowingHttpRequests {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            shouldBeEmpty()
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "CODE_RESENT"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun unconfirmedUserRescuedFromTheLoginEntryPointCompletesRegistration() = malaiAcceptanceTest {
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
        and(cognitoResendConfirmationCodeReturnsSuccessful())
        and(cognitoConfirmSignUpReturnsSession("some-verification-session"))
        and(cognitoVerificationSessionExchangeReturns(
            session = "some-verification-session",
            idToken = "some-id-token",
            accessToken = "some-access-token",
            refreshToken = "some-refresh-token",
            expiresIn = 2400,
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(activateOnboardingAccountHolderForUserReturnsSuccess(someUserId()))
        and(activateOnboardingAccountsForUserReturnsSuccess(someUserId()))

        whenever(authServiceReceives(
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

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("some-confirmation-code")
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To "some-access-token"
                "refreshToken" To "some-refresh-token"
                "idToken" To "some-id-token"
                "expiresIn" To 2400
                noExtraProps()
            }
        }

        and(theLastResponseHas()) {
            setCookies.access() shouldBe SessionCookieMatcher(value = "some-access-token", maxAge = "2592000")
            setCookies.refresh() shouldBe SessionCookieMatcher(value = "some-refresh-token", maxAge = "2592000")
        }

        and(theUserInTheDatabase(someUserId())) {
            this shouldBeEqualUsingFields UserEntity(
                id = someUserId(),
                email = someEmail(),
                firstName = "Alice",
                lastName = "Smith",
                dateOfBirth = LocalDate.parse("1985-03-02"),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                createdBy = someUserId().toString()
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 2
            this[0]["action"] shouldBe "CODE_RESENT"
            this[0]["actor_id"] shouldBe someUserId()
            this[1]["action"] shouldBe "VERIFICATION_SUCCEEDED"
            this[1]["actor_id"] shouldBe someUserId()
        }
    }

    @Test
    fun verifyCodeCompletesRegistrationAndSignsTheUserIn() = malaiAcceptanceTest {
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
        and(cognitoConfirmSignUpReturnsSession("some-verification-session"))
        and(cognitoVerificationSessionExchangeReturns(
            session = "some-verification-session",
            idToken = "some-id-token",
            accessToken = "some-access-token",
            refreshToken = "some-refresh-token",
            expiresIn = 2400,
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(activateOnboardingAccountHolderForUserReturnsSuccess(someUserId()))
        and(activateOnboardingAccountsForUserReturnsSuccess(someUserId()))

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("some-confirmation-code")
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To "some-access-token"
                "refreshToken" To "some-refresh-token"
                "idToken" To "some-id-token"
                "expiresIn" To 2400
                noExtraProps()
            }
        }

        and(theLastResponseHas()) {
            setCookies.access() shouldBe SessionCookieMatcher(value = "some-access-token", maxAge = "2592000")
            setCookies.refresh() shouldBe SessionCookieMatcher(value = "some-refresh-token", maxAge = "2592000")
        }

        and(theUserInTheDatabase(someUserId())) {
            this shouldBeEqualUsingFields UserEntity(
                id = someUserId(),
                email = someEmail(),
                firstName = "Alice",
                lastName = "Smith",
                dateOfBirth = LocalDate.parse("1985-03-02"),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                createdBy = someUserId().toString()
            )
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.ConfirmSignUp"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "Username" To someEmail()
                "ConfirmationCode" To "some-confirmation-code"
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "AuthFlow" To "USER_AUTH"
                "Session" To "some-verification-session"
                "AuthParameters" To json {
                    "USERNAME" To someEmail()
                }
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            url.endsWith("/api/v1/internal/onboarding/account-holders/activate/${someUserId()}")
        }) {
            method shouldBe POST
        }

        and(authServiceMadeTheFollowingHttpRequest {
            url.endsWith("/api/v1/internal/onboarding/accounts/activate/${someUserId()}")
        }) {
            method shouldBe POST
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "VERIFICATION_SUCCEEDED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "User"
            this[0]["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun loginStartsAnEmailOtpChallengeForAConfirmedUser() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
        and(cognitoStartLoginChallengeReturnsSession("some-login-challenge-session"))

        whenever(authServiceReceives(
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

        and(authServiceMadeTheFollowingHttpRequest {
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

        and(theLastResponseHas()) {
            setCookies.challenge() shouldBe SessionCookieMatcher(value = "some-login-challenge-session", maxAge = "600")
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "LOGIN_CHALLENGE_STARTED"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun loginWithAnUnknownEmailIsIndistinguishableFromAKnownOne() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/login")
                .body(aDefaultLoginRequest()
                    .withEmail("nobody@example.com")
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        and(theLastResponseHas()) {
            setCookies.challenge()?.copy(value = "fabricated") shouldBe
                SessionCookieMatcher(value = "fabricated", maxAge = "600")
        }

        and(authServiceMadeTheFollowingHttpRequests {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            shouldBeEmpty()
        }

        and(theAuditEventsInTheDatabase()) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun verifyCodeAnswersTheLoginChallengeAndSignsInAConfirmedUser() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
        and(cognitoAnswerLoginChallengeReturns(
            session = "some-login-challenge-session",
            idToken = "some-id-token",
            accessToken = "some-access-token",
            refreshToken = "some-refresh-token",
            expiresIn = 2400,
        ))

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .header("Cookie", "challenge_session=some-login-challenge-session")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("some-login-code")
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To "some-access-token"
                "refreshToken" To "some-refresh-token"
                "idToken" To "some-id-token"
                "expiresIn" To 2400
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.RespondToAuthChallenge"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "ChallengeName" To "EMAIL_OTP"
                "Session" To "some-login-challenge-session"
                "ChallengeResponses" To json {
                    "USERNAME" To someEmail()
                    "EMAIL_OTP_CODE" To "some-login-code"
                }
            }
        }

        and(theLastResponseHas()) {
            setCookies.access() shouldBe SessionCookieMatcher(value = "some-access-token", maxAge = "2592000")
            setCookies.refresh() shouldBe SessionCookieMatcher(value = "some-refresh-token", maxAge = "2592000")
            setCookies.challenge() shouldBe SessionCookieMatcher(value = "", maxAge = "0")
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "VERIFICATION_SUCCEEDED"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun verifyCodeAcceptsTheChallengeSessionFromTheRequestBodyWhenNoCookie() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
        and(cognitoAnswerLoginChallengeReturns(
            session = "some-login-challenge-session",
            idToken = "some-id-token",
            accessToken = "some-access-token",
            refreshToken = "some-refresh-token",
            expiresIn = 2400,
        ))

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("some-login-code")
                    .withChallengeSession("some-login-challenge-session")
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To "some-access-token"
                "refreshToken" To "some-refresh-token"
                "idToken" To "some-id-token"
                "expiresIn" To 2400
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.RespondToAuthChallenge"
        }) {
            method shouldBe POST
            body containsProp json {
                "Session" To "some-login-challenge-session"
            }
        }
    }

    @Test
    fun refreshToken() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(cognitoRefreshTokenReturns(
            "some-new-access-token",
            "some-new-id-token",
            1200
        ))

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/refresh-token")
                .body(aDefaultRefreshTokenRequest()
                    .withUserId(someUserId())
                    .withRefreshToken("some-refresh-token")
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accessToken" To "some-new-access-token"
                "refreshToken" To "some-refresh-token"
                "idToken" To "some-new-id-token"
                "expiresIn" To 1200
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            body containsProp json {
                "AuthFlow" To "REFRESH_TOKEN_AUTH"
            }
        }

        and(theLastResponseHas()) {
            setCookies.access() shouldBe SessionCookieMatcher(value = "some-new-access-token", maxAge = "2592000")
            setCookies.refresh() shouldBe SessionCookieMatcher(value = "some-refresh-token", maxAge = "2592000")
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "REFRESH_ROTATED"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun refreshTokenReadsRefreshTokenFromCookieWhenBodyOmitsIt() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(cognitoRefreshTokenReturns())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/refresh-token")
                .header("Cookie", "refresh_token=cookie-refresh-token")
                .body(aDefaultRefreshTokenRequest()
                    .withUserId(someUserId())
                    .withRefreshToken(null)
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        // Cognito received the value from the cookie, not the (null) body field.
        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            body containsProp json {
                "AuthParameters" To json {
                    "REFRESH_TOKEN" To "cookie-refresh-token"
                }
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "REFRESH_ROTATED"
            this.single()["actor_id"] shouldBe someUserId()
        }
    }

    @Test
    fun refreshTokenFromAColdLoadDerivesTheUserFromTheExpiredAccessCookie() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(cognitoRefreshTokenReturns())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/refresh-token")
                .header(
                    "Cookie",
                    "access_token=${generateExpiredAccessTokenForUserId(someUserId())}; refresh_token=cookie-refresh-token"
                )
                .body(aDefaultRefreshTokenRequest()
                    .withUserId(null)
                    .withRefreshToken(null)
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accessToken" To "some-new-cognito-access-token"
                "refreshToken" To "cookie-refresh-token"
                "idToken" To "some-new-cognito-id-token"
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            body containsProp json {
                "AuthFlow" To "REFRESH_TOKEN_AUTH"
                "AuthParameters" To json {
                    "REFRESH_TOKEN" To "cookie-refresh-token"
                }
            }
        }

        and(theLastResponseHas()) {
            setCookies.access() shouldBe SessionCookieMatcher(value = "some-new-cognito-access-token", maxAge = "2592000")
            setCookies.refresh() shouldBe SessionCookieMatcher(value = "cookie-refresh-token", maxAge = "2592000")
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "REFRESH_ROTATED"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }

    @Test
    fun signOut() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(cognitoSignOutReturnsSuccessful())

        whenever(authServiceReceives(
            post()
                .url("/api/v1/auth/signout")
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.GlobalSignOut"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
        }

        // Both cookies cleared with Max-Age=0 so the browser drops them on the same
        // {name, path} tuple they were originally set with. Empty value is mandatory —
        // omitting it would leave the cookie's last value behind in some browsers.
        and(theLastResponseHas()) {
            setCookies.access() shouldBe SessionCookieMatcher(value = "", maxAge = "0")
            setCookies.refresh() shouldBe SessionCookieMatcher(value = "", maxAge = "0")
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this.single()["action"] shouldBe "LOGOUT"
            this.single()["actor_type"] shouldBe "CUSTOMER"
            this.single()["actor_id"] shouldBe someUserId()
            this.single()["resource_type"] shouldBe "User"
            this.single()["resource_id"] shouldBe someUserId().toString()
        }
    }
}

private val org.springframework.http.ResponseEntity<String>.setCookies: List<String>
    get() = headers["Set-Cookie"] ?: emptyList()

private fun List<String>.access(): SessionCookieMatcher? = matcherFor("access_token")
private fun List<String>.refresh(): SessionCookieMatcher? = matcherFor("refresh_token")
private fun List<String>.challenge(): SessionCookieMatcher? = matcherFor("challenge_session")

private fun List<String>.matcherFor(name: String): SessionCookieMatcher? =
    firstOrNull { it.startsWith("$name=") }
        ?.let { setCookie ->
            val attrs = setCookie.split(";").map { it.trim() }
            SessionCookieMatcher(
                value = attrs.first().substringAfter("="),
                maxAge = attrs.firstOrNull { it.startsWith("Max-Age=") }?.substringAfter("="),
                httpOnly = attrs.any { it.equals("HttpOnly", ignoreCase = true) },
                secure = attrs.any { it.equals("Secure", ignoreCase = true) },
                sameSite = attrs.firstOrNull { it.startsWith("SameSite=") }?.substringAfter("="),
                path = attrs.firstOrNull { it.startsWith("Path=") }?.substringAfter("="),
            )
        }

/**
 * Comparable view of a Set-Cookie attribute set so tests can write
 *
 *     setCookies.access() shouldBe SessionCookieMatcher(value = "...", maxAge = "3600")
 *
 * and have the comparison cover the security-critical attributes (HttpOnly, SameSite=Lax,
 * Path=/) by default. `secure` is false in the test profile so HTTP MockMvc / RestClient
 * round-trips the cookie back; production sets it to true via application.properties.
 */
private data class SessionCookieMatcher(
    val value: String,
    val maxAge: String?,
    val httpOnly: Boolean = true,
    val secure: Boolean = false,
    val sameSite: String? = "Lax",
    val path: String? = "/",
)
