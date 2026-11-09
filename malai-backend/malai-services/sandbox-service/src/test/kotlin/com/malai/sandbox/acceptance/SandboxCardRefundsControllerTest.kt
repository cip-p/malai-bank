package com.malai.sandbox.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID

class SandboxCardRefundsControllerTest : SandboxAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000a1")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000b1")

    @Test
    fun resolvesTheSettledPurchaseServerSideAndForwardsTheRefund() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(resolveAuthorisationReturns(
            authorisationId = authorisationId,
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            status = "SETTLED",
            amountMinorUnits = 4370,
            currency = "GBP",
        ))
        and(triggerCardRefundAccepts())

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-refunds")
                .header("Content-Type", "application/json")
                .body(aRefundRequestBody(authorisationId))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }
        and(theStubReceivedTheRefundForTheSettledPurchase())
    }

    @Test
    fun returns404WhenTheAuthorisationIsUnknownOrForeign() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(resolveAuthorisationReturnsNotFound(authorisationId, someAccountHolderUid()))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-refunds")
                .header("Content-Type", "application/json")
                .body(aRefundRequestBody(authorisationId))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }

    @Test
    fun returns400WhenThePurchaseIsNotYetSettled() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(resolveAuthorisationReturns(
            authorisationId = authorisationId,
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            status = "AUTHORISED",
            amountMinorUnits = 4370,
            currency = "GBP",
        ))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-refunds")
                .header("Content-Type", "application/json")
                .body(aRefundRequestBody(authorisationId))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7966"
            }
        }
    }

    @Test
    fun returns400WhenAccountHolderHeaderMissing() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-refunds")
                .header("Content-Type", "application/json")
                .body(aRefundRequestBody(authorisationId))
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7999"
            }
        }
    }

    @Test
    fun returns401WhenUnauthenticated() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-refunds")
                .header("Content-Type", "application/json")
                .body(aRefundRequestBody(authorisationId))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    private fun aRefundRequestBody(authorisationId: UUID): String =
        """{"authorisationId":"$authorisationId"}"""

    private fun theStubReceivedTheRefundForTheSettledPurchase() = CallableAction {
        wiremock().verify(
            postRequestedFor(urlEqualTo("${WithMalaiStub.CONTEXT_PATH}/scenarios/cards/refund"))
                .withRequestBody(equalToJson(
                    """
                    {
                      "schemeReference": "$schemeReference",
                      "amountMinorUnits": 4370,
                      "currency": "GBP"
                    }
                    """.trimIndent()
                ))
        )
    }
}
