package com.malai.client.malai

import tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import tools.jackson.databind.ObjectMapper
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.jacksonMapperBuilder

abstract class AbstractMalaiClient(
    private val httpClient: CloseableHttpClient,
    protected val malaiBaseUrl: String
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    protected fun makeRequest(
        request: HttpUriRequestBase,
        expectedStatusCode: Int,
        toleratedStatusCodes: Set<Int> = emptySet(),
    ): String? =
        httpClient.execute(request) { response ->
            val statusCode = response.code
            val responseBody = response.entity?.let { EntityUtils.toString(it) }
            if (statusCode != expectedStatusCode) {
                if (statusCode in toleratedStatusCodes) {
                    log.debug("received tolerated status code: $statusCode, response body: $responseBody")
                } else {
                    log.error("received unexpected status code: $statusCode, response body: $responseBody")
                }
                throw MalaiClientResponseException(statusCode, expectedStatusCode, responseBody)
            }
            responseBody
        }

    protected fun makeRequestForBytes(request: HttpUriRequestBase, expectedStatusCode: Int): ByteArray? =
        httpClient.execute(request) { response ->
            val statusCode = response.code
            val bytes = response.entity?.let { EntityUtils.toByteArray(it) }
            if (statusCode != expectedStatusCode) {
                throw MalaiClientResponseException(statusCode, expectedStatusCode, bytes?.let { String(it) })
            }
            bytes
        }

    protected fun <T> fromJson(json: String, clazz: Class<T>): T =
        objectMapper.readValue(json, clazz)

    protected fun toJson(obj: Any): String =
        objectMapper.writeValueAsString(obj)
}
