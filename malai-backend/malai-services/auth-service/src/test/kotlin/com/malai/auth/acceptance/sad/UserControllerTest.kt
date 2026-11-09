package com.malai.auth.acceptance.sad

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.client.malai.auth.builders.request.UpdateUserRequestBuilder.Companion.aDefaultUpdateUserRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED


class UserControllerTest : AuthAcceptanceTest() {

    @Test
    fun getUserReturns404WhenUserIsNotInDatabase() = malaiAcceptanceTest {
        given(cognitoGetUserReturns(someUserId(), someEmail(), "some-name"))

        whenever(authServiceReceives(
            get()
                .url("/api/v1/user")
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8979"
                noExtraProps()
            }
        }
    }

    @Test
    fun getUserReturns401WhenUserIdAndCognitoUserIdDoNotMatch() = malaiAcceptanceTest {
        given(cognitoGetUserReturns(anotherUserId(), someEmail(), "some-name"))

        whenever(authServiceReceives(
            get()
                .url("/api/v1/user")
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5978"
                noExtraProps()
            }
        }
    }

    @Test
    fun getUserReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
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

    @Test
    fun updateUserReturns404WhenUserIsNotInDatabase() = malaiAcceptanceTest {
        given(cognitoGetUserReturns(someUserId(), someEmail(), "some-name"))

        whenever(authServiceReceives(
            put()
                .url("/api/v1/user")
                .body(aDefaultUpdateUserRequest()
                    .withFirstName("Alicia")
                    .withLastName("Smith"))
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8979"
                noExtraProps()
            }
        }
    }

    @Test
    fun updateUserReturns401WhenUserIdAndCognitoUserIdDoNotMatch() = malaiAcceptanceTest {
        given(cognitoGetUserReturns(anotherUserId(), someEmail(), "some-name"))

        whenever(authServiceReceives(
            put()
                .url("/api/v1/user")
                .body(aDefaultUpdateUserRequest()
                    .withFirstName("Alicia")
                    .withLastName("Smith"))
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5978"
                noExtraProps()
            }
        }
    }

    @Test
    fun updateUserReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            put()
                .url("/api/v1/user")
                .body(aDefaultUpdateUserRequest()
                    .withFirstName("Alicia")
                    .withLastName("Smith"))
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
