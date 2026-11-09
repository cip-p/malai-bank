package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.AuthService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.IdentityProvider
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.WithCognito
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

/**
 * End-to-end equivalent of [AuthTest], but driving auth-service over the HttpOnly cookie path
 * the webapp will use. Verifies the full passwordless round-trip on real services in Docker:
 *
 *   - `/login` returns `Set-Cookie: challenge_session=…` (short-lived) and reveals nothing in
 *     the body beyond "code sent"
 *   - `/verify-code` reads the Challenge session from the `challenge_session` cookie, signs the
 *     user in with `Set-Cookie: access_token=…` + `refresh_token=…`, and clears `challenge_session`
 *   - secured endpoints (`/api/v1/user`, account-holder service) accept the `access_token` cookie
 *     in lieu of an `Authorization: Bearer …` header
 *   - `/refresh-token` accepts an empty body on a cold load: it reads the refresh token from
 *     the `refresh_token` cookie and derives the user from the `access_token` cookie's JWT
 *     (signature verified, expiry ignored — ADR-0002), responding with rotated cookies
 *   - `/signout` accepts the cookie too, calls Cognito's GlobalSignOut, and clears both
 *     session cookies on the response (Max-Age=0)
 *
 * The complementary header-based path is covered by [AuthTest] — the two tests together
 * exercise dual-mode auth as the resource servers see it.
 */
class AuthCookieTest : MalaiE2ETest(), WithCognito {

    @Test
    fun `register, verify, login and refresh via HttpOnly cookies`() = malaiTest {

        given(cognitoCanRegisterUser(someEmail(), someUserId()))
        and(cognitoCanIssueServiceToken())
        and(cognitoCanVerifyRegistration(someUserId()))

        whenever(authServiceReceivesRegisterPasswordless(
            firstName = "Bob",
            lastName = "Jones",
            email = someEmail(),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        whenever(authServiceReceivesVerifyCode(someEmail(), "some-confirmation-code"))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        val accessTokenCookieAfterRegister = cookieValueFromLastResponse("access_token")

        given(cognitoCanSignOut())

        whenever(authServiceReceivesSignOutViaCookie(accessTokenCookieAfterRegister))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        assertCookieClearedInLastResponse("access_token")
        assertCookieClearedInLastResponse("refresh_token")

        given(cognitoCanStartLoginChallenge("e2e-login-challenge-session"))

        whenever(authServiceReceivesLoginPasswordless(someEmail()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "message" To "A confirmation code has been sent."
                noExtraProps()
            }
        }

        val challengeSessionCookie = cookieValueFromLastResponse("challenge_session")

        given(cognitoCanAnswerLoginChallenge(someUserId(), "e2e-login-challenge-session"))

        whenever(authServiceReceivesVerifyCodeViaChallengeCookie(someEmail(), "some-login-code", challengeSessionCookie))

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

        val accessTokenCookie = cookieValueFromLastResponse("access_token")
        val refreshTokenCookie = cookieValueFromLastResponse("refresh_token")
        assertCookieClearedInLastResponse("challenge_session")

        given(cognitoCanGetUser(someEmail(), someUserId(), "Bob Jones"))

        whenever(authServiceReceivesGetUserViaCookie(accessTokenCookie))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "firstName" To "Bob"
                "lastName" To "Jones"
                "email" To someEmail()
                "dateOfBirth" To something
                "roles" To listOf("CUSTOMER")
                noExtraProps()
            }
        }

        whenever(accountHolderServiceReceivesGetAllAccountHoldersViaCookie(accessTokenCookie))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To listOf(
                    json {
                        "accountHolderUid" To something
                        "accountHolderName" To "Bob Jones"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        given(cognitoCanRefreshToken(someUserId()))

        whenever(authServiceReceivesRefreshTokenViaCookies(accessTokenCookie, refreshTokenCookie))

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

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
                && body?.contains("REFRESH_TOKEN_AUTH") == true
        }) {
            body containsProp json {
                "AuthFlow" To "REFRESH_TOKEN_AUTH"
                "AuthParameters" To json {
                    "REFRESH_TOKEN" To refreshTokenCookie
                }
            }
        }

        val rotatedAccessTokenCookie = cookieValueFromLastResponse("access_token")

        whenever(authServiceReceivesSignOutViaCookie(rotatedAccessTokenCookie))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theFollowingHttpRequestWasMade(AuthService, IdentityProvider) {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.GlobalSignOut"
                && body?.contains(rotatedAccessTokenCookie) == true
        }) {
            body containsProp json {
                "AccessToken" To rotatedAccessTokenCookie
            }
        }

        assertCookieClearedInLastResponse("access_token")
        assertCookieClearedInLastResponse("refresh_token")
    }
}
