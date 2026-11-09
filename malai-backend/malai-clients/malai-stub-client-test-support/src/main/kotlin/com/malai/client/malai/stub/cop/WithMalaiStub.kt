package com.malai.client.malai.stub.cop

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.stub.card.builders.response.CardMerchantsResponseBuilder
import com.malai.client.malai.stub.card.builders.response.CardPurchaseResultBuilder
import com.malai.client.malai.stub.cop.builders.response.StubCopLookupResponseBuilder
import com.malai.client.malai.stub.directory.builders.response.CounterpartiesResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext

interface WithMalaiStub : WithWiremock {

    fun stubCopLookupReturns(response: StubCopLookupResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/cop/lookup"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun getCounterpartiesReturns(response: CounterpartiesResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/scenarios/counterparties"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun postInboundFpsAccepts() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/scenarios/inbound/fps"))
                .willReturn(aResponse().withStatus(204))
        )
    }

    fun postInboundBacsAccepts() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/scenarios/inbound/bacs"))
                .willReturn(aResponse().withStatus(204))
        )
    }

    fun postCollectionBacsAccepts() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/scenarios/collections/bacs"))
                .willReturn(aResponse().withStatus(204))
        )
    }

    fun getCardMerchantsReturns(response: CardMerchantsResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/scenarios/cards/merchants"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun triggerCardPurchaseReturns(response: CardPurchaseResultBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/scenarios/cards/purchase"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun presentPendingCardsReturns(presented: Int) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/scenarios/cards/present-now"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"presented":$presented}""")
                )
        )
    }

    fun triggerCardRefundAccepts() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/scenarios/cards/refund"))
                .willReturn(aResponse().withStatus(202))
        )
    }

    fun registerCardPanAccepts() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/scenarios/cards/pans"))
                .willReturn(aResponse().withStatus(202))
        )
    }

    companion object {
        const val CONTEXT_PATH = "/malai-stub"
    }
}
