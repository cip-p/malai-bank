package com.malai.test.acceptance.kensa.stub

import com.malai.client.malai.TestBuilder
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import java.util.concurrent.atomic.AtomicReference

class HttpClientStub(
    private val restClient: RestClient,
    /**
     * Where the most-recently-received response is recorded. Defaults to a stub-local
     * [AtomicReference] for the acceptance-test case where there's exactly one stub. In e2e
     * tests, multiple stubs (one per service) share a single holder so
     * [receivedResponse]/[lastResponse] can be asked on any of them and return the most recent
     * response regardless of which service replied to it.
     */
    private val latestHttpResponse: AtomicReference<ResponseEntity<String>> = AtomicReference(),
) {

    fun sends(httpRequest: HttpRequest) = Action<ActionContext> {
        var request: RestClient.RequestBodySpec = restClient.method(httpRequest.method).uri { uriBuilder ->
            uriBuilder.path(httpRequest.url)
            httpRequest.queryParams.forEach { (name, values) ->
                values.forEach { value -> uriBuilder.queryParam(name, value) }
            }
            uriBuilder.build()
        }
        httpRequest.headers.forEach { (name, value) ->
            request = request.header(name, value)
        }
        if (httpRequest.body != null) {
            val body = httpRequest.body
            val contentType = httpRequest.contentType ?: MediaType.APPLICATION_JSON
            request = when (body) {
                is ByteArray -> request.body(body)
                is String -> request.body(body)
                is TestBuilder -> request.body(body.build())
                else -> request.body(testingObjectMapper.writeValueAsString(body))
            }.contentType(contentType)
        }

        val responseBody = request.retrieve().toEntity<String>()
        latestHttpResponse.set(responseBody)
    }

    fun receivedResponse() = StateCollector<ResponseEntity<String>> { _ ->
        latestHttpResponse
            .get()
    }

    fun lastResponse(): ResponseEntity<String> =
        latestHttpResponse.get()
            ?: throw IllegalStateException("No HTTP response captured yet — did the test call `when(xxxServiceReceives(...))`?")
}

data class HttpRequest(
    val method: HttpMethod,
    val url: String,
    val body: Any? = null,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, List<String>> = emptyMap(),
    val contentType: MediaType? = null,
) {

    class HttpRequestBuilder private constructor() {

        private lateinit var method: HttpMethod
        private lateinit var url: String
        private var body: Any? = null
        private var contentType: MediaType? = null
        private var headers: MutableMap<String, String> = mutableMapOf()
        private var queryParams: MutableMap<String, MutableList<String>> = mutableMapOf()

        fun method(method: HttpMethod) = apply { this.method = method }
        fun url(url: String) = apply { this.url = url }
        fun header(headerName: String, headerValue: String) = apply { this.headers[headerName] = headerValue }
        fun queryParam(name: String, value: String) = apply {
            queryParams.getOrPut(name) { mutableListOf() }.add(value)
        }
        fun queryParam(name: String, value: Any) = queryParam(name, value.toString())
        fun body(body: String) = apply { this.body = body }
        fun body(body: TestBuilder) = apply { this.body = body }
        fun body(body: ByteArray) = apply { this.body = body }
        fun contentType(contentType: MediaType) = apply { this.contentType = contentType }
        fun contentType(contentType: String) = contentType(MediaType.parseMediaType(contentType))

        fun build() = HttpRequest(method, url, body, headers, queryParams, contentType)

        companion object {
            fun post() = HttpRequestBuilder()
                .method(HttpMethod.POST)

            fun get() = HttpRequestBuilder()
                .method(HttpMethod.GET)

            fun put() = HttpRequestBuilder()
                .method(HttpMethod.PUT)

            fun delete() = HttpRequestBuilder()
                .method(HttpMethod.DELETE)
        }
    }
}
