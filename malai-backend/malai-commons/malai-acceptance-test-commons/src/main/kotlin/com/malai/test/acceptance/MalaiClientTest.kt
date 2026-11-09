package com.malai.test.acceptance

import tools.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.malai.client.malai.MalaiTestFixtures
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher
import io.kotest.matchers.should
import com.neovibrant.kacey.matchers.Prop
import dev.kensa.StateCollector
import dev.kensa.junit.KensaTest
import dev.kensa.kotest.WithKotest
import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import com.malai.client.malai.WithWiremock as ClientWithWiremock

/**
 * Shared base for client-module BDD tests. Mirrors what [MalaiAcceptanceTest] provides for
 * service tests — modern Kensa scaffolding ([KensaTest] + [WithKotest]), shared fixture values
 * ([MalaiTestFixtures]), WireMock setup, a [madeTheFollowingHttpRequest] state collector, and
 * a [containsProp] JSON matcher — so concrete test classes only declare their `With*Api`
 * mix-ins and test methods.
 *
 * Concrete test classes must be annotated with `@MalaiClientSpringTest` to bring up the Spring
 * context that wires the client bean under test.
 *
 * Bridges both client-side ([ClientWithWiremock], lifecycle) and acceptance-side ([WithWiremock],
 * used by every existing `With*Api` interface) WireMock interfaces onto the same server instance
 * so existing service-stub interfaces work unchanged in client tests.
 */
abstract class MalaiClientTest :
    KensaTest,
    WithKotest,
    MalaiTestFixtures,
    ClientWithWiremock,
    WithWiremock {

    override fun wiremock(): WireMockServer = ClientWithWiremock.wireMockServer

    override fun madeTheHttpRequest(requestPatternBuilder: RequestPatternBuilder): Any? {
        wiremock().verify(requestPatternBuilder)
        return null
    }

    /**
     * Returns a [StateCollector] that yields the first downstream HTTP request the client under
     * test sent to a WireMock-stubbed dependency during the current test that satisfies
     * [matching]. Use inside a Kensa `then(...)` / `and(...)` block — see [MalaiAcceptanceTest]'s
     * `malaiServiceMadeTheFollowingHttpRequest` for the equivalent on the service side.
     */
    fun madeTheFollowingHttpRequest(
        matching: CapturedHttpRequest.() -> Boolean,
    ): StateCollector<CapturedHttpRequest> = StateCollector { _ ->
        val capturedRequests = wiremock().allServeEvents
            .sortedBy { it.request.loggedDate }
            .map { it.request.toCapturedHttpRequest() }

        capturedRequests.firstOrNull(matching)
            ?: error(
                "Expected the client to have made a downstream HTTP request matching the given " +
                    "predicate, but none of the ${capturedRequests.size} captured request(s) matched:\n" +
                    capturedRequests.joinToString("\n") { "  - ${it.method} ${it.url}" }
            )
    }

    infix fun String?.containsProp(json: Prop) {
        if (this == null) {
            fail("The JSON string was null")
        }

        val responsePayload = testingObjectMapper.readValue<Prop>(this)

        responsePayload should ApiJsonMatcher.containsProp(json)
    }

    companion object {
        init {
            if (!WireMockHolder.isInitialized) {
                WireMockHolder.set(ClientWithWiremock.wireMockServer)
            }
        }
    }
}
