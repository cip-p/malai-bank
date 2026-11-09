package com.malai.client.malai.tracking

import com.malai.client.malai.MalaiHttpClientCustomizer
import com.malai.commons.tracking.KensaEventEnvelope
import com.malai.commons.tracking.KensaEventKind
import com.malai.commons.tracking.KensaEventPublisher
import com.malai.commons.tracking.MalaiSourceContext
import com.malai.commons.tracking.TestIdContext
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Adds a [MalaiHttpClientCustomizer] that publishes Kensa HTTP_REQUEST / HTTP_RESPONSE envelopes
 * for every outbound call so the e2e test JVM's [KensaEventConsumer] can draw the corresponding
 * arrow on the sequence diagram. Method, URL, headers, body and status are all forwarded.
 *
 * Implemented as a single HC5 `ExecChainHandler` so one interceptor sees both the request and the
 * response on the caller thread — that's where `MalaiSourceContext` / `TestIdContext` ThreadLocals
 * are accessible. Body manipulation re-sets each entity as a [ByteArrayEntity] so subsequent
 * handlers (the wire send for the request, the caller's read for the response) see the cached
 * bytes rather than a consumed stream.
 *
 * Profile-gated to `e2e-tests` only — production builds don't load it.
 */
@Configuration
@Profile("e2e-tests")
class KensaHttpClientConfig(
    private val publisher: KensaEventPublisher,
    @Value("\${spring.application.name:unknown}") private val applicationName: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun kensaCaptureMalaiHttpClientCustomizer(): MalaiHttpClientCustomizer =
        MalaiHttpClientCustomizer { builder ->
            // FIRST, not LAST: kensa must wrap the entire chain from the caller's side. HC5 5.5's
            // ContentCompressionExec runs BETWEEN our kensa and MAIN_TRANSPORT (it's a chain
            // element added at FIRST position by HttpClientBuilder). On the response path it
            // checks `entity.getContentEncoding()` and only wraps a DecompressingEntity if that
            // header is set on the entity. If kensa ran with LAST (which HC5 5.5 actually slots
            // BEFORE MAIN_TRANSPORT — see HttpClientBuilder line 1045), it would see the raw
            // gzipped entity, cache its bytes, replace it with a ByteArrayEntity, and that
            // ByteArrayEntity has no Content-Encoding — so ContentCompressionExec then skips
            // decompression and the caller reads gzipped bytes. With FIRST, kensa is outermost:
            // ContentCompressionExec has already wrapped the entity in a DecompressingEntity by
            // the time we read it, so EntityUtils.toByteArray reads decompressed bytes cleanly.
            builder.addExecInterceptorFirst("kensa") { request, scope, chain ->
                val testId = TestIdContext.valueOrNull?.value
                if (testId == null) {
                    chain.proceed(request, scope)
                } else {
                    publishRequest(testId, request)
                    val response = chain.proceed(request, scope)
                    publishResponse(testId, request, response)
                    response
                }
            }
        }

    private fun publishRequest(testId: String, request: ClassicHttpRequest) {
        val source = MalaiSourceContext.valueOrNull?.name ?: applicationName
        val bodyJson = cacheRequestBody(request)
        publisher.publish(
            KensaEventEnvelope(
                testId = testId,
                kind = KensaEventKind.HTTP_REQUEST,
                tsEpochMillis = System.currentTimeMillis(),
                fromParty = source,
                httpMethod = request.method,
                httpUrl = request.uri.toString(),
                bodyJson = bodyJson,
                headers = capturedRequestHeaders(request),
            )
        )
    }

    /**
     * Reads the headers explicitly set on the request PLUS synthesises `Content-Type` and
     * `Content-Length` from the entity. HC5's `RequestContent` interceptor (inside ProtocolExec)
     * is what would normally promote the entity's content-type into a header on the wire, but
     * that runs AFTER our kensa exec interceptor — so the envelope would miss them. The values
     * we synthesise match what's actually sent on the wire.
     */
    private fun capturedRequestHeaders(request: ClassicHttpRequest): Map<String, String> {
        val headers = request.headers.associate { it.name to it.value }.toMutableMap()
        val entity = request.entity ?: return headers
        entity.contentType?.let { headers.putIfAbsent("Content-Type", it) }
        if (entity.contentLength >= 0) {
            headers.putIfAbsent("Content-Length", entity.contentLength.toString())
        }
        return headers
    }

    private fun publishResponse(testId: String, request: ClassicHttpRequest, response: ClassicHttpResponse) {
        val source = MalaiSourceContext.valueOrNull?.name ?: applicationName
        val bodyJson = cacheResponseBody(response)
        publisher.publish(
            KensaEventEnvelope(
                testId = testId,
                kind = KensaEventKind.HTTP_RESPONSE,
                tsEpochMillis = System.currentTimeMillis(),
                toParty = source,
                httpMethod = request.method,
                httpUrl = request.uri.toString(),
                httpStatus = response.code,
                bodyJson = bodyJson,
                headers = response.headers.associate { it.name to it.value },
            )
        )
    }

    private fun cacheRequestBody(request: ClassicHttpRequest): String? {
        val entity = request.entity ?: return null
        return try {
            val bytes = EntityUtils.toByteArray(entity)
            val ct = entity.contentType?.let(ContentType::parse)
            request.entity = ByteArrayEntity(bytes, ct)
            bodyAsString(bytes, ct)
        } catch (e: Exception) {
            log.debug("could not cache request body for kensa: {}", e.message)
            null
        }
    }

    private fun cacheResponseBody(response: ClassicHttpResponse): String? {
        val entity = response.entity ?: return null
        return try {
            val bytes = EntityUtils.toByteArray(entity)
            val ct = entity.contentType?.let(ContentType::parse)
            response.entity = ByteArrayEntity(bytes, ct)
            bodyAsString(bytes, ct)
        } catch (e: Exception) {
            log.debug("could not cache response body for kensa: {}", e.message)
            null
        }
    }

    private fun bodyAsString(bytes: ByteArray, contentType: ContentType?): String {
        val isText = contentType?.mimeType?.lowercase()?.let {
            it.startsWith("text/")
                || it.contains("json")
                || it.contains("xml")
                || it.contains("form-urlencoded")
        } ?: true
        return if (isText) String(bytes) else "<${bytes.size} bytes>"
    }
}
