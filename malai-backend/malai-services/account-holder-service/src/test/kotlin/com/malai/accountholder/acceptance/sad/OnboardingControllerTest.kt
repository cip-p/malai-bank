package com.malai.accountholder.acceptance.sad

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.client.malai.accountholder.builders.request.CreateOnboardingAccountHolderRequestBuilder.Companion.aDefaultCreateOnboardingAccountHolderRequest
import com.malai.client.malai.accountholder.builders.request.CreateOnboardingIndividualRequestBuilder.Companion.aDefaultCreateOnboardingIndividualRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.*

class OnboardingControllerTest : AccountHolderAcceptanceTest() {

    @Test
    fun rejectCreateAccountHolderWithoutServiceJwt() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders")
                .body(aDefaultCreateOnboardingAccountHolderRequest()
                    .withUserId(someUserId()))
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
    fun rejectCreateAccountHolderWithUserJwt() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders")
                .body(aDefaultCreateOnboardingAccountHolderRequest()
                    .withUserId(someUserId()))
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
    fun rejectCreateAccountHolderWhenUserIdIsMissing() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders")
                .body(aDefaultCreateOnboardingAccountHolderRequest()
                    .withUserId(null))
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
    fun rejectCreateIndividualWhenAccountHolderUnknown() = malaiAcceptanceTest {
        whenever(accountHolderServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/account-holders/${someAccountHolderUid()}/individual")
                .body(aDefaultCreateOnboardingIndividualRequest())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8990"
                noExtraProps()
            }
        }
    }
}
