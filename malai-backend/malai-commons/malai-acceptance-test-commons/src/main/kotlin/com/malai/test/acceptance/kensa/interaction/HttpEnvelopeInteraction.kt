package com.malai.test.acceptance.kensa.interaction

/**
 * Captures one HTTP request that was published as a `KensaEventEnvelope` (kind = HTTP_REQUEST)
 * from inside a service container. Carried over the wire so the test-JVM renderer can produce
 * the same kind of arrow Kensa renders today for WireMock-captured traffic.
 */
data class HttpEnvelopeRequest(
    val method: String,
    val url: String,
    val bodyJson: String?,
    val headers: Map<String, String>,
)

/**
 * Captures one HTTP response that was published as a `KensaEventEnvelope` (kind = HTTP_RESPONSE).
 */
data class HttpEnvelopeResponse(
    val status: Int,
    val bodyJson: String?,
    val headers: Map<String, String>,
)
