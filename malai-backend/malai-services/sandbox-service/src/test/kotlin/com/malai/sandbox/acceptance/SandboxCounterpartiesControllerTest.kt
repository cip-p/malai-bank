package com.malai.sandbox.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.client.malai.stub.directory.builders.response.CounterpartiesResponseBuilder.Companion.aCounterpartiesResponse
import com.malai.client.malai.stub.directory.builders.response.CounterpartyResponseBuilder.Companion.aCounterpartyResponse
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED

class SandboxCounterpartiesControllerTest : SandboxAcceptanceTest() {

    @Test
    fun returnsTheCatalogueFetchedFromTheStub() = malaiAcceptanceTest {
        given(getCounterpartiesReturns(
            aCounterpartiesResponse().withCounterparties(
                aCounterpartyResponse()
                    .withSortCode("60-00-01")
                    .withAccountNumber("12345678")
                    .withBank("NatWest")
                    .withNameOnRecord("Alice Walker")
                    .withOutboundOutcome("SETTLES"),
                aCounterpartyResponse()
                    .withSortCode("60-00-01")
                    .withAccountNumber("99000001")
                    .withBank("NatWest")
                    .withNameOnRecord("Jane Closed")
                    .withOutboundOutcome("RETURNS")
                    .withReturnReason("ACCOUNT_CLOSED"),
                aCounterpartyResponse()
                    .withSortCode("23-00-06")
                    .withAccountNumber("88000001")
                    .withBank("Starling")
                    .withNameOnRecord("Limbo Holdings Ltd")
                    .withOutboundOutcome("STUCK_PENDING"),
            )
        ))

        whenever(sandboxServiceReceives(
            get()
                .url("/api/v1/sandbox/counterparties")
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "counterparties" To listOf(
                    json {
                        "sortCode" To "60-00-01"
                        "accountNumber" To "12345678"
                        "bank" To "NatWest"
                        "nameOnRecord" To "Alice Walker"
                        "outboundOutcome" To "SETTLES"
                        noExtraProps()
                    },
                    json {
                        "sortCode" To "60-00-01"
                        "accountNumber" To "99000001"
                        "bank" To "NatWest"
                        "nameOnRecord" To "Jane Closed"
                        "outboundOutcome" To "RETURNS"
                        "returnReason" To "ACCOUNT_CLOSED"
                        noExtraProps()
                    },
                    json {
                        "sortCode" To "23-00-06"
                        "accountNumber" To "88000001"
                        "bank" To "Starling"
                        "nameOnRecord" To "Limbo Holdings Ltd"
                        "outboundOutcome" To "STUCK_PENDING"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theStubReceivedTheCounterpartiesRequest())
    }

    @Test
    fun returns401WhenTheRequestIsUnauthenticated() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            get().url("/api/v1/sandbox/counterparties")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    private fun theStubReceivedTheCounterpartiesRequest() = CallableAction {
        wiremock().verify(getRequestedFor(urlEqualTo("${WithMalaiStub.CONTEXT_PATH}/scenarios/counterparties")))
    }
}
