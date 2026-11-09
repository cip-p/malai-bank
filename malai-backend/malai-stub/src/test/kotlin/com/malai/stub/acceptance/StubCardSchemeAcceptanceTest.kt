package com.malai.stub.acceptance

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.eventually
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.OK

class StubCardSchemeAcceptanceTest : MalaiStubAcceptanceTest() {

    private val schemeReference = "00000000-0000-4000-8000-0000000000ab"

    @Test
    fun getMerchantsReturnsTheFictionalCardMerchantDirectory() = malaiAcceptanceTest {
        whenever(malaiStubReceives(get().url("/scenarios/cards/merchants")))

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
                    json {
                        "merchantId" To "orbit-transit"
                        "name" To "Orbit Transit"
                        "mcc" To "4111"
                        noExtraProps()
                    },
                    json {
                        "merchantId" To "lumen-streaming"
                        "name" To "Lumen Streaming"
                        "mcc" To "4899"
                        noExtraProps()
                    },
                    json {
                        "merchantId" To "harbour-fuel"
                        "name" To "Harbour Fuel"
                        "mcc" To "5541"
                        noExtraProps()
                    },
                    json {
                        "merchantId" To "atlas-electronics"
                        "name" To "Atlas Electronics"
                        "mcc" To "5732"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun registersACardPanAndListsItInThePanRegistry() = malaiAcceptanceTest {
        val pan = "9904320000055551"

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/pans")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "pan": "$pan"
                    }
                    """.trimIndent()
                )
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        whenever(malaiStubReceives(get().url("/scenarios/cards/pans")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body!! shouldContain pan
        }
    }

    @Test
    fun postCardPurchaseOriginatesASchemeAuthAtTheCardGatewayAndRelaysTheApproval() = malaiAcceptanceTest {
        given(theCardGatewayWillRespond(schemeReference, outcome = "APPROVED"))
        and(theCardGatewayWillAcceptPresentments())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/purchase")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "pan": "9904320000012341",
                      "merchantId": "fictional-coffee",
                      "amountMinorUnits": 4370,
                      "currency": "GBP"
                    }
                    """.trimIndent()
                )
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To schemeReference
                "outcome" To "APPROVED"
                "declineReasonCode" To null
                noExtraProps()
            }
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$CARD_GATEWAY_PATH/cards/callbacks/authorise"
        }) {
            method shouldBe POST
            body containsProp json {
                "schemeReference" To something
                "pan" To "9904320000012341"
                "amount" To json {
                    "minorUnits" To 4370
                    "currency" To "GBP"
                    noExtraProps()
                }
                "merchantName" To "Fictional Coffee Co"
                "mcc" To "5814"
                noExtraProps()
            }
        }
    }

    @Test
    fun postCardPurchaseRelaysADeclineWithItsReasonCode() = malaiAcceptanceTest {
        given(theCardGatewayWillRespond(schemeReference, outcome = "DECLINED", declineReasonCode = "62"))

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/purchase")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "pan": "9904320000012341",
                      "merchantId": "nimbus-groceries",
                      "amountMinorUnits": 1599,
                      "currency": "GBP"
                    }
                    """.trimIndent()
                )
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
    fun anApprovedPurchaseIsAutomaticallyPresentedToTheGatewayOnTheScheduledThreadCarryingTheTestId() = malaiAcceptanceTest {
        given(theCardGatewayWillRespond(schemeReference, outcome = "APPROVED"))
        and(theCardGatewayWillAcceptPresentments())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/purchase")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "pan": "9904320000012341",
                      "merchantId": "fictional-coffee",
                      "amountMinorUnits": 4370,
                      "currency": "GBP"
                    }
                    """.trimIndent()
                )
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To schemeReference
                "outcome" To "APPROVED"
                "declineReasonCode" To null
                noExtraProps()
            }
        }

        and(theCardGatewayEventuallyReceivedAPresentmentFor(merchantName = "Fictional Coffee Co", amountMinorUnits = 4370))
    }

    @Test
    fun presentNowForcesAPendingPurchaseToPresentImmediately() = malaiAcceptanceTest {
        given(theCardGatewayWillRespond(schemeReference, outcome = "APPROVED"))
        and(theCardGatewayWillAcceptPresentments())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/purchase")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "pan": "9904320000012341",
                      "merchantId": "orbit-transit",
                      "amountMinorUnits": 250,
                      "currency": "GBP"
                    }
                    """.trimIndent()
                )
        ))

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/present-now")
                .header("Content-Type", "application/json")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "presented" To something
                noExtraProps()
            }
        }

        and(theCardGatewayEventuallyReceivedAPresentmentFor(merchantName = "Orbit Transit", amountMinorUnits = 250))
    }

    @Test
    fun postCardReverseOriginatesAReversalAtTheCardGateway() = malaiAcceptanceTest {
        given(theCardGatewayWillAcceptReversals())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/reverse")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "schemeReference": "$schemeReference"
                    }
                    """.trimIndent()
                )
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$CARD_GATEWAY_PATH/cards/callbacks/reverse"
        }) {
            method shouldBe POST
            body containsProp json {
                "schemeReference" To schemeReference
                noExtraProps()
            }
        }
    }

    @Test
    fun postCardRefundOriginatesARefundAtTheCardGateway() = malaiAcceptanceTest {
        given(theCardGatewayWillAcceptRefunds())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/cards/refund")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "schemeReference": "$schemeReference",
                      "amountMinorUnits": 4370,
                      "currency": "GBP"
                    }
                    """.trimIndent()
                )
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$CARD_GATEWAY_PATH/cards/callbacks/refund"
        }) {
            method shouldBe POST
            body containsProp json {
                "schemeReference" To schemeReference
                "refundReference" To something
                "amount" To json {
                    "minorUnits" To 4370
                    "currency" To "GBP"
                    noExtraProps()
                }
                noExtraProps()
            }
        }
    }

    private fun theCardGatewayEventuallyReceivedAPresentmentFor(merchantName: String, amountMinorUnits: Long) = CallableAction {
        val testId = TestIdContext.value.value
        eventually {
            val received = wiremock().allServeEvents.any { event ->
                event.request.url == "$CARD_GATEWAY_PATH/cards/callbacks/present" &&
                    event.request.headers.getHeader(TestId.HEADER).containsValue(testId) &&
                    event.request.bodyAsString.contains(merchantName) &&
                    event.request.bodyAsString.contains("\"minorUnits\":$amountMinorUnits")
            }
            check(received) {
                "card presentment callback not yet received for testId=$testId merchant=$merchantName"
            }
        }
    }
}
