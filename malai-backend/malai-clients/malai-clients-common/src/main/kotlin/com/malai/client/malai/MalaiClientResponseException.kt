package com.malai.client.malai

class MalaiClientResponseException(
    val statusCode: Int,
    val expectedStatusCode: Int,
    val responseBody: String?,
) : RuntimeException(
    "received invalid status code: $statusCode, expected: $expectedStatusCode, body: ${responseBody ?: "<empty>"}",
)
