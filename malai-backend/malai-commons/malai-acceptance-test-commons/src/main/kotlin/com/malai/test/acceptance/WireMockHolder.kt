package com.malai.test.acceptance

import com.github.tomakehurst.wiremock.WireMockServer

/**
 * Shared holder for the single [WireMockServer] used by the malai test infrastructure.
 *
 * Acceptance tests populate this from [MalaiAcceptanceTestContext.boot], and e2e tests
 * populate this from their own `MalaiE2ETestContext.boot` — in both cases a single WireMock
 * instance is started once per JVM and reused across the whole test run. The
 * [WithWiremock] interface resolves the server from this holder so it doesn't have to know
 * which kind of test is running.
 */
object WireMockHolder {

    lateinit var server: WireMockServer
        private set

    val isInitialized: Boolean get() = ::server.isInitialized

    fun set(server: WireMockServer) {
        this.server = server
    }
}
