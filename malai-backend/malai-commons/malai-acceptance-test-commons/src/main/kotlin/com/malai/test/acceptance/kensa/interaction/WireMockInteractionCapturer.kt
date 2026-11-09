package com.malai.test.acceptance.kensa.interaction

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import com.malai.commons.tracking.TestId
import com.malai.test.acceptance.kensa.CapturedInteractionHolder
import dev.kensa.state.CapturedInteractionBuilder
import dev.kensa.state.CapturedInteractions
import dev.kensa.state.Party

/**
 * Captures downstream HTTP traffic that the service under test sends to a WireMock-stubbed
 * dependency, so it renders on Kensa's sequence diagram alongside the incoming request.
 *
 * Each service supplies a [fromSystemLookup] that determines the [Party] initiating the
 * request (e.g. the service under test in acceptance tests, or the service identified by
 * the `X-Malai-Source` header in e2e tests where multiple services share a WireMock), and
 * a [toSystemLookup] that maps the requested URL to the party of the downstream dependency
 * being stubbed.
 *
 * Requests are captured in `afterMatch` (when WireMock receives them) and responses in
 * `transform` (when WireMock has the upstream response in hand but before sending it back),
 * so that for nested proxy calls the inner arrows land between the outer request and response
 * in chronological order on the sequence diagram.
 */
class WireMockInteractionCapturer(
    private val fromSystemLookup: FromSystemLookup,
    private val toSystemLookup: PartyLookupByUrl,
    /**
     * Opt-out filter for traffic that should NOT render on the Kensa sequence diagram even
     * though it passes through this WireMock instance. Returns `true` to capture the
     * interaction (default), `false` to skip. Used e.g. to hide service-bookkeeping calls
     * (Cognito service-token fetching) so the diagram only shows the business interactions.
     */
    private val shouldCapture: (ServeEvent) -> Boolean = { true },
) : ServeEventListener, ResponseTransformerV2 {

    /**
     * Convenience constructor for the acceptance-test case where the service under test is
     * fixed for the whole JVM (so every captured WireMock call originates from the same party).
     */
    constructor(fromSystem: Party, toSystemLookup: PartyLookupByUrl) : this(
        FromSystemLookup { _ -> fromSystem },
        toSystemLookup,
    )

    override fun getName(): String = WireMockInteractionCapturer.name

    override fun applyGlobally(): Boolean = true

    override fun afterMatch(serveEvent: ServeEvent, parameters: Parameters) {
        val interactions = interactionsFor(serveEvent) ?: return
        val fromSystem = fromSystemLookup(serveEvent)
        val toSystem = toSystemLookup(serveEvent.request.url)
        interactions.capture(
            CapturedInteractionBuilder.from(fromSystem)
                .to(toSystem)
                .with(serveEvent.request, "${describe(serveEvent)} Request")
        )
    }

    override fun transform(response: Response, serveEvent: ServeEvent): Response {
        val interactions = interactionsFor(serveEvent) ?: return response
        val fromSystem = fromSystemLookup(serveEvent)
        val toSystem = toSystemLookup(serveEvent.request.url)
        interactions.capture(
            CapturedInteractionBuilder.from(toSystem)
                .to(fromSystem)
                .with(response, "${describe(serveEvent)} Response")
        )
        return response
    }

    private fun interactionsFor(serveEvent: ServeEvent): CapturedInteractions? {
        if (!serveEvent.request.containsHeader(TestId.HEADER)) return null
        if (!shouldCapture(serveEvent)) return null
        val testId = TestId(serveEvent.request.header(TestId.HEADER).firstValue())
        return CapturedInteractionHolder[testId]
    }

    private fun describe(serveEvent: ServeEvent) = InteractionDescriber.describe(
        serveEvent.request.method.value(),
        serveEvent.request.url,
        serveEvent.request.headers.all().associate { it.key() to it.firstValue() }
    )

    companion object {
        val name: String = WireMockInteractionCapturer::class.simpleName!!
    }
}

fun interface PartyLookupByUrl {
    operator fun invoke(url: String): Party
}

fun interface FromSystemLookup {
    operator fun invoke(serveEvent: ServeEvent): Party
}
