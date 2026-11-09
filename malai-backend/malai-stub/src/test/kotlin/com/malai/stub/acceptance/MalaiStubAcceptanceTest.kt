package com.malai.stub.acceptance

import com.malai.stub.support.WithStubGateways
import com.malai.stub.support.runStubService
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class MalaiStubAcceptanceTest : MalaiAcceptanceTest(), WithStubGateways {

    fun malaiStubReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun malaiStubMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun malaiStubMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    companion object {
        const val FPS_GATEWAY_PATH = "/fps-gateway"
        const val SWIFT_GATEWAY_PATH = "/swift-gateway"
        const val BACS_GATEWAY_PATH = "/bacs-gateway"
        const val CARD_GATEWAY_PATH = "/card-gateway"

        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Malai Stub Acceptance Tests",
                fromSystem = MalaiParty.MalaiStub,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(FPS_GATEWAY_PATH) -> MalaiParty.FpsGateway
                        url.startsWith(SWIFT_GATEWAY_PATH) -> MalaiParty.SwiftGateway
                        url.startsWith(BACS_GATEWAY_PATH) -> MalaiParty.BacsGateway
                        url.startsWith(CARD_GATEWAY_PATH) -> MalaiParty.CardGateway
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runStubService(
                    fpsGatewayBaseUrl = wiremock.url(FPS_GATEWAY_PATH),
                    swiftGatewayBaseUrl = wiremock.url(SWIFT_GATEWAY_PATH),
                    bacsGatewayBaseUrl = wiremock.url(BACS_GATEWAY_PATH),
                    cardGatewayBaseUrl = wiremock.url(CARD_GATEWAY_PATH),
                )
            }
        }
    }
}
