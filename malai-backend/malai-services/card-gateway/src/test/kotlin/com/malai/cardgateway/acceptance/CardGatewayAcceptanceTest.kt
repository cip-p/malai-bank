package com.malai.cardgateway.acceptance

import com.malai.cardgateway.support.CapturedCardEvents
import com.malai.cardgateway.support.runCardGatewayService
import com.malai.client.malai.card.WithCards
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithOAuth2Service
import com.malai.test.acceptance.eventually
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector

abstract class CardGatewayAcceptanceTest : MalaiAcceptanceTest(), WithCards, WithOAuth2Service {

    fun cardGatewayReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun cardGatewayMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun theProducedPresentmentEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedCardEvents().presentedFor(testId).isNotEmpty()) {
                "no presentment event produced for testId=$testId"
            }
        }
        capturedCardEvents().presentedFor(testId)
    }

    fun theProducedReversalEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedCardEvents().reversedFor(testId).isNotEmpty()) {
                "no reversal event produced for testId=$testId"
            }
        }
        capturedCardEvents().reversedFor(testId)
    }

    fun theProducedRefundEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedCardEvents().refundedFor(testId).isNotEmpty()) {
                "no refund event produced for testId=$testId"
            }
        }
        capturedCardEvents().refundedFor(testId)
    }

    private fun capturedCardEvents(): CapturedCardEvents = getBean(CapturedCardEvents::class.java)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Card Gateway Acceptance Tests",
                fromSystem = MalaiParty.CardGateway,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithCards.CONTEXT_PATH) -> MalaiParty.CardService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runCardGatewayService(
                    cardBaseUrl = wiremock.url(WithCards.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
