package com.malai.client.malai

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

@ExtendWith(WithWiremock.WiremockExtension::class)
interface WithWiremock {

    fun wiremock(): WireMockServer = wireMockServer

    fun madeTheHttpRequest(requestPatternBuilder: RequestPatternBuilder): Any? {
        wiremock().verify(requestPatternBuilder)
        return null
    }

    class WiremockExtension : BeforeAllCallback, BeforeEachCallback {

        override fun beforeAll(extensionContext: ExtensionContext) {
            if (!wireMockServer.isRunning) {
                wireMockServer.start()
            }
        }

        override fun beforeEach(extensionContext: ExtensionContext) {
            wireMockServer.resetAll()
        }
    }

    companion object {
        val wireMockServer = WireMockServer(wireMockConfig().port(7777))
    }
}
