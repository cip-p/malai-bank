package com.malai.test.e2e.kensa

import com.malai.commons.tracking.KensaEventEnvelope
import com.malai.commons.tracking.KensaEventKind
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.kensa.testingObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

/**
 * E2e replacement for [com.malai.test.acceptance.kensa.interaction.MalaiHttpInteractionCapturer].
 * Publishes the top-level Client → Service HTTP request/response envelopes from the test JVM to
 * `kensa-events.fifo` so they interleave correctly with the server-side envelopes on the diagram.
 *
 * Publish is synchronous (`.get()` on the SDK future) so the call returns only once SQS has
 * acknowledged receipt. That fixes the ordering bug where the test's direct-write of the response
 * arrow landed in the holder before the inter-service envelopes had finished their round trip.
 *
 * Acceptance tests still use the in-process [MalaiHttpInteractionCapturer] — they don't need an
 * envelope round-trip because there's no cross-JVM transport involved.
 */
class KensaEnvelopePublishingHttpInterceptor(
    private val sqsAsyncClient: SqsAsyncClient,
    private val kensaEventsQueueUrlProvider: () -> String,
) : ClientHttpRequestInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val testId = TestIdContext.value
        request.headers.add(TestId.HEADER, testId.value)

        publish(buildRequestEnvelope(testId, request, body))
        val response = BodyCachingResponse(execution.execute(request, body))
        publish(buildResponseEnvelope(testId, request, response))
        return response
    }

    private fun buildRequestEnvelope(testId: TestId, request: HttpRequest, body: ByteArray) =
        KensaEventEnvelope(
            testId = testId.value,
            kind = KensaEventKind.HTTP_REQUEST,
            tsEpochMillis = System.currentTimeMillis(),
            fromParty = CLIENT_PARTY_NAME,
            httpMethod = request.method.name(),
            httpUrl = request.uri.toString(),
            bodyJson = body.takeIf { it.isNotEmpty() }?.let { String(it) },
            headers = request.headers.toSingleValueMap(),
        )

    private fun buildResponseEnvelope(testId: TestId, request: HttpRequest, response: BodyCachingResponse) =
        KensaEventEnvelope(
            testId = testId.value,
            kind = KensaEventKind.HTTP_RESPONSE,
            tsEpochMillis = System.currentTimeMillis(),
            toParty = CLIENT_PARTY_NAME,
            httpMethod = request.method.name(),
            httpUrl = request.uri.toString(),
            httpStatus = response.statusCode.value(),
            bodyJson = response.cachedBody.takeIf { it.isNotEmpty() }?.let { String(it) },
            headers = response.headers.toSingleValueMap(),
        )

    private fun publish(envelope: KensaEventEnvelope) {
        try {
            sqsAsyncClient.sendMessage { send ->
                send.queueUrl(kensaEventsQueueUrlProvider())
                    .messageBody(testingObjectMapper.writeValueAsString(envelope))
                    .messageGroupId(envelope.testId)
                    .messageDeduplicationId(UUID.randomUUID().toString())
            }.get()
        } catch (e: Exception) {
            log.warn("failed to publish kensa envelope kind={}: {}", envelope.kind, e.message)
        }
    }

    companion object {
        const val CLIENT_PARTY_NAME: String = "Client"

        private class BodyCachingResponse(private val original: ClientHttpResponse) : ClientHttpResponse by original {
            val cachedBody: ByteArray = original.body.use { it.readBytes() }
            override fun close() {}
            override fun getBody(): InputStream = ByteArrayInputStream(cachedBody)
        }
    }
}
