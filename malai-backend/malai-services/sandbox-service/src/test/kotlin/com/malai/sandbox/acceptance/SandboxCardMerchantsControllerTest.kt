package com.malai.sandbox.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.client.malai.stub.card.builders.response.CardMerchantResponseBuilder.Companion.aCardMerchantResponse
import com.malai.client.malai.stub.card.builders.response.CardMerchantsResponseBuilder.Companion.aCardMerchantsResponse
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED

class SandboxCardMerchantsControllerTest : SandboxAcceptanceTest() {

    @Test
    fun listsTheStubCardMerchantDirectory() = malaiAcceptanceTest {
        given(getCardMerchantsReturns(
            aCardMerchantsResponse().withMerchants(
                aCardMerchantResponse()
                    .withMerchantId("fictional-coffee")
                    .withName("Fictional Coffee Co")
                    .withMcc("5814"),
                aCardMerchantResponse()
                    .withMerchantId("nimbus-groceries")
                    .withName("Nimbus Groceries")
                    .withMcc("5411"),
            )
        ))

        whenever(sandboxServiceReceives(
            get()
                .url("/api/v1/sandbox/card-merchants")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "merchants" To listOf(
                    json {
                        "merchantId" To "fictional-coffee"
                        "name" To "Fictional Coffee Co"
                        "mcc" To "5814"
                        noExtraProps()
                    },
                    json {
                        "merchantId" To "nimbus-groceries"
                        "name" To "Nimbus Groceries"
                        "mcc" To "5411"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
        and(theStubReceivedTheMerchantsRequest())
    }

    @Test
    fun returns401WhenUnauthenticated() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(get().url("/api/v1/sandbox/card-merchants")))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    private fun theStubReceivedTheMerchantsRequest() = CallableAction {
        wiremock().verify(
            getRequestedFor(urlEqualTo("${WithMalaiStub.CONTEXT_PATH}/scenarios/cards/merchants"))
        )
    }
}
