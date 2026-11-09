package com.malai.stub.support

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.stub.acceptance.MalaiStubAcceptanceTest.Companion.BACS_GATEWAY_PATH
import com.malai.stub.acceptance.MalaiStubAcceptanceTest.Companion.CARD_GATEWAY_PATH
import com.malai.stub.acceptance.MalaiStubAcceptanceTest.Companion.FPS_GATEWAY_PATH
import com.malai.stub.acceptance.MalaiStubAcceptanceTest.Companion.SWIFT_GATEWAY_PATH
import com.malai.test.acceptance.WithWiremock
import com.malai.test.acceptance.kensa.CallableAction

interface WithStubGateways : WithWiremock {

    fun theCardGatewayWillRespond(schemeReference: String, outcome: String, declineReasonCode: String? = null) =
        CallableAction {
            val declineField = declineReasonCode?.let { ""","declineReasonCode":"$it"""" } ?: ""
            wiremock().stubFor(
                post(urlEqualTo("$CARD_GATEWAY_PATH/cards/callbacks/authorise"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"schemeReference":"$schemeReference","outcome":"$outcome"$declineField}"""),
                    ),
            )
        }

    fun theCardGatewayWillAcceptPresentments() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$CARD_GATEWAY_PATH/cards/callbacks/present"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theCardGatewayWillAcceptReversals() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$CARD_GATEWAY_PATH/cards/callbacks/reverse"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theCardGatewayWillAcceptRefunds() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$CARD_GATEWAY_PATH/cards/callbacks/refund"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theFpsGatewayWillAcceptSettlementCallbacks() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$FPS_GATEWAY_PATH/fps/callbacks/settlement"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theFpsGatewayWillAcceptReturnCallbacks() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$FPS_GATEWAY_PATH/fps/callbacks/return"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theFpsGatewayWillAcceptInboundCallbacks() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$FPS_GATEWAY_PATH/fps/callbacks/inbound"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theSwiftGatewayWillAcceptSettlementCallbacks() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$SWIFT_GATEWAY_PATH/swift/callbacks/settlement"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theBacsGatewayWillAcceptInboundCallbacks() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$BACS_GATEWAY_PATH/bacs/callbacks/inbound"))
                .willReturn(aResponse().withStatus(202)),
        )
    }

    fun theBacsGatewayWillAcceptCollectionCallbacks() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$BACS_GATEWAY_PATH/bacs/callbacks/collection"))
                .willReturn(aResponse().withStatus(202)),
        )
    }
}
