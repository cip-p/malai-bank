package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountHolderService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AuthService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.IdentityProvider
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.e2e.AuditEventInDb
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.WithCognito
import com.malai.test.e2e.mapAuditEvents
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.*
import java.util.*

class AuthTest : MalaiE2ETest(), WithCognito {

    @Test
    fun `register, verify, sign out, login, verify, refresh and sign out`() = malaiTest {

        given(theAuditChainIsAtGenesis())
        and(cognitoCanRegisterUser(someEmail(), someUserId()))
        and(cognitoCanIssueServiceToken())

        whenever(authServiceReceivesRegisterPasswordless(
            firstName = "Alice",
            lastName = "Smith",
            email = someEmail(),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        and(cognitoCanVerifyRegistration(someUserId()))

        whenever(authServiceReceivesVerifyCode(someEmail(), "some-confirmation-code"))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To something
                "refreshToken" To something
                "idToken" To something
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        and(cognitoCanSignOut())

        whenever(authServiceReceivesSignOut(someUserId()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(cognitoCanStartLoginChallenge("e2e-login-challenge-session"))

        whenever(authServiceReceivesLoginPasswordless(someEmail()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        val challengeSession = cookieValueFromLastResponse("challenge_session")

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
                && body?.contains("EMAIL_OTP") == true
        }) {
            method shouldBe POST
            body containsProp json {
                "AuthFlow" To "USER_AUTH"
                "AuthParameters" To json {
                    "USERNAME" To someEmail()
                    "PREFERRED_CHALLENGE" To "EMAIL_OTP"
                }
            }
        }

        and(cognitoCanAnswerLoginChallenge(someUserId(), "e2e-login-challenge-session"))

        whenever(authServiceReceivesVerifyCodeWithChallengeSession(someEmail(), "some-login-code", challengeSession))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To something
                "refreshToken" To something
                "idToken" To something
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        val refreshTokenAfterLogin = refreshTokenFromLastResponse()

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.RespondToAuthChallenge"
        }) {
            method shouldBe POST
            body containsProp json {
                "ChallengeName" To "EMAIL_OTP"
                "Session" To "e2e-login-challenge-session"
                "ChallengeResponses" To json {
                    "USERNAME" To someEmail()
                    "EMAIL_OTP_CODE" To "some-login-code"
                }
            }
        }

        whenever(accountHolderServiceReceivesGetAllAccountHolders(someUserId()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To listOf(
                    json {
                        "accountHolderUid" To something
                        "accountHolderName" To "Alice Smith"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        and(cognitoCanRefreshToken(someUserId()))

        whenever(authServiceReceivesRefreshToken(someUserId(), refreshTokenAfterLogin))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accessToken" To something
                "refreshToken" To something
                "idToken" To something
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        whenever(authServiceReceivesSignOut(someUserId()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theAuditEventsInTheDatabase()) {
            mapAuditEvents() shouldBe listOf(
                AuditEventInDb(1, someUserId(), "SYSTEM", "ACCOUNT_HOLDER_ONBOARDED"),
                AuditEventInDb(2, null, "SYSTEM", "INDIVIDUAL_ONBOARDED"),
                AuditEventInDb(3, null, "SYSTEM", "ADDRESS_ONBOARDED"),
                AuditEventInDb(4, someUserId(), "SYSTEM", "ACCOUNT_ONBOARDED"),
                AuditEventInDb(5, someUserId(), "CUSTOMER", "REGISTERED"),
                AuditEventInDb(6, someUserId(), "SYSTEM", "ACCOUNT_HOLDERS_ACTIVATED"),
                AuditEventInDb(7, someUserId(), "SYSTEM", "ACCOUNTS_ACTIVATED"),
                AuditEventInDb(8, someUserId(), "CUSTOMER", "VERIFICATION_SUCCEEDED"),
                AuditEventInDb(9, someUserId(), "CUSTOMER", "LOGOUT"),
                AuditEventInDb(10, someUserId(), "CUSTOMER", "LOGIN_CHALLENGE_STARTED"),
                AuditEventInDb(11, someUserId(), "CUSTOMER", "VERIFICATION_SUCCEEDED"),
                AuditEventInDb(12, someUserId(), "CUSTOMER", "REFRESH_ROTATED"),
                AuditEventInDb(13, someUserId(), "CUSTOMER", "LOGOUT"),
            )
        }
    }

    @Test
    fun `passwordless register, verify and use the session`() = malaiTest {

        given(theAuditChainIsAtGenesis())
        and(cognitoCanRegisterUser(someEmail(), someUserId()))
        and(cognitoCanIssueServiceToken())

        whenever(authServiceReceivesRegisterPasswordless(
            firstName = "Alice",
            lastName = "Smith",
            email = someEmail(),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.SignUp"
        }) {
            method shouldBe POST
            body containsProp json {
                "Username" To someEmail()
            }
            body!! shouldNotContain "Password"
        }

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.AdminAddUserToGroup"
        }) {
            method shouldBe POST
            body containsProp json {
                "Username" To someEmail()
                "GroupName" To "customer"
            }
        }

        and(theFollowingHttpRequestWasMade(AuthService, AccountHolderService) {
            url.endsWith("/api/v1/internal/onboarding/account-holders")
        }) {
            method shouldBe POST
            body containsProp json {
                "userId" To someUserId().toString()
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AuthService, AccountService) {
            url.endsWith("/api/v1/internal/onboarding/accounts")
        }) {
            method shouldBe POST
        }

        and(cognitoCanVerifyRegistration(someUserId()))

        whenever(authServiceReceivesVerifyCode(someEmail(), "some-confirmation-code"))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To something
                "refreshToken" To something
                "idToken" To something
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.ConfirmSignUp"
        }) {
            method shouldBe POST
            body containsProp json {
                "Username" To someEmail()
                "ConfirmationCode" To "some-confirmation-code"
            }
        }

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
                && body?.contains("USER_AUTH") == true
        }) {
            method shouldBe POST
            body containsProp json {
                "AuthFlow" To "USER_AUTH"
                "Session" To "e2e-verification-session"
            }
        }

        and(theFollowingHttpRequestWasMade(AuthService, AccountHolderService) {
            url.endsWith("/api/v1/internal/onboarding/account-holders/activate/${someUserId()}")
        }) {
            method shouldBe POST
        }

        and(theFollowingHttpRequestWasMade(AuthService, AccountService) {
            url.endsWith("/api/v1/internal/onboarding/accounts/activate/${someUserId()}")
        }) {
            method shouldBe POST
        }

        whenever(accountHolderServiceReceivesGetAllAccountHolders(someUserId()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To listOf(
                    json {
                        "accountHolderUid" To something
                        "accountHolderName" To "Alice Smith"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        val accountHolderUid: UUID = uuidFromLastResponseList("accountHolders", 0, "accountHolderUid")

        whenever(accountServiceReceivesGetAccounts(
            userId = someUserId(),
            accountHolderId = accountHolderUid,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            // Three accounts, not one: verifying registration runs the registration
            // seeder, which opens the credit-card and savings accounts alongside the
            // current account so a self-registered evaluator lands on the same estate
            // as the seeded demo customer. Ordered as the accounts endpoint returns
            // them — current, then credit, then savings, matching the order they are
            // created in.
            body containsProp json {
                "accounts" To listOf(
                    json {
                        "accountUid" To something
                        "accountType" To "PRIMARY"
                        "defaultCategory" To something
                        "currency" To "GBP"
                        "createdAt" To something
                        "name" To "Personal"
                        noExtraProps()
                    },
                    json {
                        "accountUid" To something
                        "accountType" To "CREDIT_CARD"
                        "defaultCategory" To something
                        "currency" To "GBP"
                        "createdAt" To something
                        "name" To "Credit Card"
                        noExtraProps()
                    },
                    json {
                        "accountUid" To something
                        "accountType" To "SAVINGS"
                        "defaultCategory" To something
                        "currency" To "GBP"
                        "createdAt" To something
                        "name" To "Savings"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            mapAuditEvents() shouldBe listOf(
                AuditEventInDb(1, someUserId(), "SYSTEM", "ACCOUNT_HOLDER_ONBOARDED"),
                AuditEventInDb(2, null, "SYSTEM", "INDIVIDUAL_ONBOARDED"),
                AuditEventInDb(3, null, "SYSTEM", "ADDRESS_ONBOARDED"),
                AuditEventInDb(4, someUserId(), "SYSTEM", "ACCOUNT_ONBOARDED"),
                AuditEventInDb(5, someUserId(), "CUSTOMER", "REGISTERED"),
                AuditEventInDb(6, someUserId(), "SYSTEM", "ACCOUNT_HOLDERS_ACTIVATED"),
                AuditEventInDb(7, someUserId(), "SYSTEM", "ACCOUNTS_ACTIVATED"),
                AuditEventInDb(8, someUserId(), "CUSTOMER", "VERIFICATION_SUCCEEDED"),
            )
        }
    }

    @Test
    fun `unconfirmed user rescued from the login entry point completes registration and signs in`() = malaiTest {

        given(theAuditChainIsAtGenesis())
        and(cognitoCanRegisterUser(someEmail(), someUserId()))
        and(cognitoCanIssueServiceToken())

        whenever(authServiceReceivesRegisterPasswordless(
            firstName = "Alice",
            lastName = "Smith",
            email = someEmail(),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        and(cognitoCanResendConfirmationCode())

        whenever(authServiceReceivesLoginPasswordless(someEmail()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.ResendConfirmationCode"
        }) {
            method shouldBe POST
            body containsProp json {
                "Username" To someEmail()
            }
        }

        and(cognitoCanVerifyRegistration(someUserId()))

        whenever(authServiceReceivesVerifyCode(someEmail(), "some-confirmation-code"))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To something
                "refreshToken" To something
                "idToken" To something
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        whenever(accountHolderServiceReceivesGetAllAccountHolders(someUserId()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To listOf(
                    json {
                        "accountHolderUid" To something
                        "accountHolderName" To "Alice Smith"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            mapAuditEvents() shouldBe listOf(
                AuditEventInDb(1, someUserId(), "SYSTEM", "ACCOUNT_HOLDER_ONBOARDED"),
                AuditEventInDb(2, null, "SYSTEM", "INDIVIDUAL_ONBOARDED"),
                AuditEventInDb(3, null, "SYSTEM", "ADDRESS_ONBOARDED"),
                AuditEventInDb(4, someUserId(), "SYSTEM", "ACCOUNT_ONBOARDED"),
                AuditEventInDb(5, someUserId(), "CUSTOMER", "REGISTERED"),
                AuditEventInDb(6, someUserId(), "CUSTOMER", "CODE_RESENT"),
                AuditEventInDb(7, someUserId(), "SYSTEM", "ACCOUNT_HOLDERS_ACTIVATED"),
                AuditEventInDb(8, someUserId(), "SYSTEM", "ACCOUNTS_ACTIVATED"),
                AuditEventInDb(9, someUserId(), "CUSTOMER", "VERIFICATION_SUCCEEDED"),
            )
        }
    }

    @Test
    fun `registering again with a taken email silently logs into the existing account`() = malaiTest {

        given(theAuditChainIsAtGenesis())
        and(cognitoCanRegisterUser(someEmail(), someUserId()))
        and(cognitoCanIssueServiceToken())

        whenever(authServiceReceivesRegisterPasswordless(
            firstName = "Alice",
            lastName = "Smith",
            email = someEmail(),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(cognitoCanVerifyRegistration(someUserId()))

        whenever(authServiceReceivesVerifyCode(someEmail(), "some-confirmation-code"))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To something
                "refreshToken" To something
                "idToken" To something
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        and(cognitoCanStartLoginChallenge("e2e-reregister-challenge-session"))

        whenever(authServiceReceivesRegisterPasswordless(
            firstName = "Mallory",
            lastName = "Hacker",
            email = someEmail(),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        val challengeSession = cookieValueFromLastResponse("challenge_session")

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
                && body?.contains("EMAIL_OTP") == true
        }) {
            method shouldBe POST
            body containsProp json {
                "AuthFlow" To "USER_AUTH"
                "AuthParameters" To json {
                    "USERNAME" To someEmail()
                    "PREFERRED_CHALLENGE" To "EMAIL_OTP"
                }
            }
        }

        and(cognitoCanAnswerLoginChallenge(someUserId(), "e2e-reregister-challenge-session"))

        whenever(authServiceReceivesVerifyCodeWithChallengeSession(someEmail(), "some-login-code", challengeSession))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "userId" To someUserId().toString()
                "accessToken" To something
                "refreshToken" To something
                "idToken" To something
                "expiresIn" To 3600
                noExtraProps()
            }
        }

        whenever(accountHolderServiceReceivesGetAllAccountHolders(someUserId()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To listOf(
                    json {
                        "accountHolderUid" To something
                        "accountHolderName" To "Alice Smith"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            mapAuditEvents() shouldBe listOf(
                AuditEventInDb(1, someUserId(), "SYSTEM", "ACCOUNT_HOLDER_ONBOARDED"),
                AuditEventInDb(2, null, "SYSTEM", "INDIVIDUAL_ONBOARDED"),
                AuditEventInDb(3, null, "SYSTEM", "ADDRESS_ONBOARDED"),
                AuditEventInDb(4, someUserId(), "SYSTEM", "ACCOUNT_ONBOARDED"),
                AuditEventInDb(5, someUserId(), "CUSTOMER", "REGISTERED"),
                AuditEventInDb(6, someUserId(), "SYSTEM", "ACCOUNT_HOLDERS_ACTIVATED"),
                AuditEventInDb(7, someUserId(), "SYSTEM", "ACCOUNTS_ACTIVATED"),
                AuditEventInDb(8, someUserId(), "CUSTOMER", "VERIFICATION_SUCCEEDED"),
                AuditEventInDb(9, someUserId(), "CUSTOMER", "LOGIN_CHALLENGE_STARTED"),
                AuditEventInDb(10, someUserId(), "CUSTOMER", "VERIFICATION_SUCCEEDED"),
            )
        }
    }

    @Test
    fun getUserRejectsRequestWithoutJwt() = malaiTest {
        whenever(authServiceReceives(
            get()
                .url("/api/v1/user")
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
