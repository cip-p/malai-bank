package com.malai.sandbox.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED

class SandboxCardPresentmentsControllerTest : SandboxAcceptanceTest() {

    @Test
    fun forcesPendingCapturesToPresentAndRelaysTheCount() = malaiAcceptanceTest {
        given(presentPendingCardsReturns(2))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-presentments")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "presented" To 2
                noExtraProps()
            }
        }
        and(theStubReceivedThePresentNowRequest())
    }

    @Test
    fun returns401WhenUnauthenticated() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(post().url("/api/v1/sandbox/card-presentments")))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    private fun theStubReceivedThePresentNowRequest() = CallableAction {
        wiremock().verify(
            postRequestedFor(urlEqualTo("${WithMalaiStub.CONTEXT_PATH}/scenarios/cards/present-now"))
        )
    }
}
