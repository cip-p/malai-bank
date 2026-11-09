package com.malai.auth.acceptance

import com.malai.client.malai.auth.builders.request.LoginRequestBuilder.Companion.aDefaultLoginRequest
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK

class TestIdHeaderTest : AuthAcceptanceTest() {

    private lateinit var testIdValue: String

    @Test
    fun `passes through X-TestId and X-Malai-Source headers`() = malaiAcceptanceTest {
        captureTestId()

        given(aUserExists(someUserId(), someEmail(), "Alice", "Smith"))
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
        }

        and(authServiceMadeTheFollowingHttpRequest {
            headers["X-Amz-Target"] == "AWSCognitoIdentityProviderService.InitiateAuth"
        }) {
            method shouldBe POST
            url shouldEndWith "/cognito/"
            headers shouldContain ("X-TestId" to testIdValue)
            headers shouldContain ("X-Malai-Source" to "auth-service")
        }
    }

    fun captureTestId() {
        this.testIdValue = TestIdContext.value.value
    }
}
