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

class SandboxInboundPaymentsControllerTest : SandboxAcceptanceTest() {

    @Test
    fun forwardsAcceptedFpsInboundToTheStub() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getAccountReturns(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            currency = "GBP",
            sortCode = "65-82-37",
            accountNumber = "55667788",
            name = "Main Account",
        ))
        and(postInboundFpsAccepts())

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "inbound-fps-key")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody(rail = "FPS"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }
        and(theStubReceivedTheInboundRequest(
            path = "/scenarios/inbound/fps",
            expectedBody = expectedStubBody(),
        ))
    }

    @Test
    fun forwardsAcceptedBacsInboundToTheStub() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getAccountReturns(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            currency = "GBP",
            sortCode = "65-82-37",
            accountNumber = "55667788",
            name = "Main Account",
        ))
        and(postInboundBacsAccepts())

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "inbound-bacs-key")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody(rail = "BACS"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }
        and(theStubReceivedTheInboundRequest(
            path = "/scenarios/inbound/bacs",
            expectedBody = expectedStubBody(),
        ))
    }

    @Test
    fun returns401WhenUnauthenticated() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "no-auth-key")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun returns400WhenAccountHolderHeaderMissing() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "no-holder-key")
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
    fun returns400WhenIdempotencyKeyMissing() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7991"
            }
        }
    }

    @Test
    fun returns404WhenCreditedAccountBelongsToAnotherHolder() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getAccountReturnsNotFound(someAccountUid(), someAccountHolderUid()))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "cross-holder-key")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody())
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
            }
        }
    }

    @Test
    fun returns400WhenRailIsUnknown() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "bad-rail-key")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody(rail = "CHAPS"))
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
    fun returns400WhenOriginatorNameIsMissing() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "no-name-key")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "rail": "FPS",
                      "creditedAccountId": "${someAccountUid()}",
                      "originatorSortCode": "60-00-01",
                      "originatorAccountNumber": "11000001",
                      "amountMinorUnits": 1500,
                      "reference": "RENT MARCH"
                    }
                    """.trimIndent(),
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
    fun returns400WhenAmountIsZero() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", "zero-amount-key")
                .header("Content-Type", "application/json")
                .body(aValidRequestBody(amountMinorUnits = 0))
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
    fun replayWithSameKeyCallsTheStubOnlyOnce() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getAccountReturns(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            currency = "GBP",
            sortCode = "65-82-37",
            accountNumber = "55667788",
            name = "Main Account",
        ))
        and(postInboundFpsAccepts())

        val request = post()
            .url("/api/v1/sandbox/inbound-payments")
            .header("Idempotency-Key", "replay-key")
            .header("Content-Type", "application/json")
            .body(aValidRequestBody())
            .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())

        whenever(sandboxServiceReceives(request))
        whenever(sandboxServiceReceives(request))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }
        and(theStubReceivedTheInboundRequestExactlyOnce("/scenarios/inbound/fps"))
    }

    private fun aValidRequestBody(rail: String = "FPS", amountMinorUnits: Int = 1500): String =
        """
        {
          "rail": "$rail",
          "creditedAccountId": "${someAccountUid()}",
          "originatorSortCode": "60-00-01",
          "originatorAccountNumber": "11000001",
          "originatorName": "Alice Walker",
          "amountMinorUnits": $amountMinorUnits,
          "reference": "RENT MARCH"
        }
        """.trimIndent()

    private fun expectedStubBody(): String =
        """
        {
          "beneficiarySortCode": "65-82-37",
          "beneficiaryAccountNumber": "55667788",
          "beneficiaryName": "Main Account",
          "originatorSortCode": "60-00-01",
          "originatorAccountNumber": "11000001",
          "originatorName": "Alice Walker",
          "amountMinorUnits": 1500,
          "currency": "GBP",
          "description": "RENT MARCH"
        }
        """.trimIndent()

    private fun theStubReceivedTheInboundRequest(path: String, expectedBody: String) = CallableAction {
        wiremock().verify(
            postRequestedFor(urlEqualTo("${WithMalaiStub.CONTEXT_PATH}$path"))
                .withRequestBody(equalToJson(expectedBody))
        )
    }

    private fun theStubReceivedTheInboundRequestExactlyOnce(path: String) = CallableAction {
        wiremock().verify(1, postRequestedFor(urlEqualTo("${WithMalaiStub.CONTEXT_PATH}$path")))
    }
}
