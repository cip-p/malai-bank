package com.malai.accounts.acceptance.sad

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.client.malai.accounts.builders.request.CreateOnboardingAccountRequestBuilder.Companion.aDefaultCreateOnboardingAccountRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED

class OnboardingAccountsControllerTest : AccountsAcceptanceTest() {

    @Test
    fun rejectCreateOnboardingAccountWithoutServiceJwt() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts")
                .body(aDefaultCreateOnboardingAccountRequest()
                    .withUserId(someUserId())
                    .withAccountHolderId(someAccountHolderUid()))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun rejectCreateOnboardingAccountWithUserJwt() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts")
                .body(aDefaultCreateOnboardingAccountRequest()
                    .withUserId(someUserId())
                    .withAccountHolderId(someAccountHolderUid()))
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        // A regular user JWT has no `client_id` claim, so it fails the service-JWT check.
        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5987"
                noExtraProps()
            }
        }
    }

    @Test
    fun rejectCreateOnboardingAccountWhenUserIdIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts")
                .body(aDefaultCreateOnboardingAccountRequest()
                    .withUserId(null)
                    .withAccountHolderId(someAccountHolderUid()))
                .withServiceJwtToken()
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
    fun rejectCreateOnboardingAccountWhenAccountHolderIdIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts")
                .body(aDefaultCreateOnboardingAccountRequest()
                    .withUserId(someUserId())
                    .withAccountHolderId(null))
                .withServiceJwtToken()
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
    fun rejectCreateOnboardingAccountWhenCurrencyIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts")
                .body(aDefaultCreateOnboardingAccountRequest()
                    .withUserId(someUserId())
                    .withAccountHolderId(someAccountHolderUid())
                    .withCurrency(null))
                .withServiceJwtToken()
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
    fun rejectCreateOnboardingAccountWhenNameIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts")
                .body(aDefaultCreateOnboardingAccountRequest()
                    .withUserId(someUserId())
                    .withAccountHolderId(someAccountHolderUid())
                    .withName(null))
                .withServiceJwtToken()
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
    fun rejectActivateAccountsWithoutServiceJwt() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts/activate/${someUserId()}")
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
