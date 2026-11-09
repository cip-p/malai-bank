package com.malai.sandbox.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.client.malai.stub.card.builders.response.CardPurchaseResultBuilder.Companion.aCardPurchaseResult
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED

class SandboxCardPurchasesControllerTest : SandboxAcceptanceTest() {

    private val schemeReference = "00000000-0000-4000-8000-0000000000ab"
    private val pan = "9904320000012341"

    @Test
    fun resolvesTheCardServerSideAndRelaysAnApprovedPurchase() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(resolveCardReturns(
            cardId = someCardUid(),
            accountHolderId = someAccountHolderUid(),
            accountId = someAccountUid(),
            pan = pan,
        ))
        and(triggerCardPurchaseReturns(aCardPurchaseResult()
            .withSchemeReference(schemeReference)
            .withOutcome("APPROVED")))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-purchases")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To schemeReference
                "outcome" To "APPROVED"
                noExtraProps()
            }
        }
        and(theStubReceivedThePurchaseWithPan())
    }

    @Test
    fun relaysADeclineWithItsReasonCode() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(resolveCardReturns(
            cardId = someCardUid(),
            accountHolderId = someAccountHolderUid(),
            accountId = someAccountUid(),
            pan = pan,
        ))
        and(triggerCardPurchaseReturns(aCardPurchaseResult()
            .withSchemeReference(schemeReference)
            .withOutcome("DECLINED")
            .withDeclineReasonCode("62")))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-purchases")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To schemeReference
                "outcome" To "DECLINED"
                "declineReasonCode" To "62"
                noExtraProps()
            }
        }
    }

    @Test
    fun returns404WhenTheCardBelongsToAnotherHolder() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(resolveCardReturnsNotFound(someCardUid(), someAccountHolderUid()))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-purchases")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
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
    fun returns400WhenAccountHolderHeaderMissing() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-purchases")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
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
    fun returns400WhenAmountIsZero() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/card-purchases")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "cardId": "${someCardUid()}",
                      "merchantId": "fictional-coffee",
                      "amountMinorUnits": 0
                    }
                    """.trimIndent()
                )
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
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
                .url("/api/v1/sandbox/card-purchases")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    private fun aValidRequestBody(): String =
        """
        {
          "cardId": "${someCardUid()}",
          "merchantId": "fictional-coffee",
          "amountMinorUnits": 4370
        }
        """.trimIndent()

    private fun theStubReceivedThePurchaseWithPan() = CallableAction {
        wiremock().verify(
            postRequestedFor(urlEqualTo("${WithMalaiStub.CONTEXT_PATH}/scenarios/cards/purchase"))
                .withRequestBody(equalToJson(
                    """
                    {
                      "pan": "$pan",
                      "merchantId": "fictional-coffee",
                      "amountMinorUnits": 4370,
                      "currency": "GBP"
                    }
                    """.trimIndent()
                ))
        )
    }
}
