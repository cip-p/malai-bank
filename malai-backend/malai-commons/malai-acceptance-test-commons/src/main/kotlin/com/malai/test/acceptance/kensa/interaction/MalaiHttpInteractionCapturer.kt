package com.malai.test.acceptance.kensa.interaction

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.kensa.CapturedInteractionHolder
import com.malai.test.acceptance.kensa.MalaiParty.Companion.Client
import dev.kensa.render.diagram.directive.ArrowStyle
import dev.kensa.state.CapturedInteractionBuilder.Companion.from
import dev.kensa.state.Party
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Captures outgoing HTTP interactions (client -> service under test) onto Kensa's sequence
 * diagram for the current test. Each service provides the [serviceParty] of the service
 * under test (e.g. `AuthService`, `AccountHolderService`); the [clientParty] defaults to
 * the universal `Client` actor.
 */
class MalaiHttpInteractionCapturer(
    private val serviceParty: Party,
    private val clientParty: Party = Client,
) : ClientHttpRequestInterceptor {

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        val testId = TestIdContext.value
        val interactions = CapturedInteractionHolder[testId]
            ?: throw IllegalStateException("The CapturedInteractions has not been registered for TestId[$testId]. Ensure this is done in a beforeEach for your test.")

        request.headers.add(TestId.HEADER, testId.value)

        val description = request.classify()
        interactions.capture(
            from(clientParty)
                .to(serviceParty)
                .arrowStyle(ArrowStyle.UmlSynchronous)
                .with(Pair(request, body), "$description Request")
        )

        val response = BodyCachingResponse(execution.execute(request, body))

        interactions.capture(
            from(serviceParty)
                .to(clientParty)
                .arrowStyle(ArrowStyle.UmlResponse)
                .with(response, "$description Response")
        )

        return response
    }

    companion object {
        private fun HttpRequest.classify(): String =
            InteractionDescriber.describe(
                method.name(),
                uri.rawPath ?: uri.path,
                headers.toSingleValueMap()
            )

        private class BodyCachingResponse(private val original: ClientHttpResponse) : ClientHttpResponse by original {
            private val bytes = original.body.use { it.readBytes() }

            override fun close() {}

            override fun getBody(): InputStream = ByteArrayInputStream(bytes)
        }
    }
}
