package com.malai.test.acceptance

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.VerificationException
import com.github.tomakehurst.wiremock.http.MultiValue
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Provides access to the [WireMockServer] booted by [MalaiAcceptanceTestContext] and helpers to
 * assert on matched/unmatched requests and stubs for the currently-running test.
 *
 * The server is started once at JVM class-load time (from the service's acceptance-test base
 * class) and kept running for the lifetime of the JVM — stopping/restarting WireMock between
 * test classes would move it to a new random port and break every outgoing request from the
 * running application.
 */
@ExtendWith(WithWiremock.WiremockExtension::class)
interface WithWiremock {

    fun wiremock(): WireMockServer = WireMockHolder.server

    fun madeTheHttpRequest(requestPatternBuilder: RequestPatternBuilder): Any? {
        wiremock().verify(requestPatternBuilder)
        return null
    }

    fun checkForUnmatchedRequests() {
        wiremock().findAllUnmatchedRequests()
            .filter {
                it.headers.getHeader(TestId.HEADER).containsValue(TestIdContext.value.value)
            }
            .takeIf { it.isNotEmpty() }
            ?.let {
                throw VerificationException.forUnmatchedRequests(it)
            }
    }

    fun checkForUnmatchedStubs() {
        wiremock()
            .findUnmatchedStubs()
            .mappings
            .filter { stubMapping ->
                val testIdHeaderMatcher = stubMapping.request.headers?.get(TestId.HEADER)
                testIdHeaderMatcher?.match(MultiValue(TestId.HEADER, listOf(TestIdContext.value.value)))
                    ?.isExactMatch
                    ?: false
            }
            .takeIf { it.isNotEmpty() }
            ?.let {
                fail("One or more wiremock requests were primed, but never received a matching request: $it")
            }
    }

    class WiremockExtension : BeforeEachCallback {
        override fun beforeEach(extensionContext: ExtensionContext) {
            WireMockHolder.server.resetAll()
        }
    }
}
