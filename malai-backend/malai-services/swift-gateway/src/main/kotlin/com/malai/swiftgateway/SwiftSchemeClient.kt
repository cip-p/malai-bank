package com.malai.swiftgateway

import com.malai.client.malai.AbstractMalaiClient
import com.malai.scheme.contracts.swift.SwiftSchemeSubmitAck
import com.malai.scheme.contracts.swift.SwiftSchemeSubmitRequest
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SwiftSchemeClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.swift-scheme.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun submit(request: SwiftSchemeSubmitRequest): SwiftSchemeSubmitAck {
        val httpRequest = HttpPost("$malaiBaseUrl/swift/payments")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 202)
            ?: throw RuntimeException("expected a 202 ack body from POST /swift/payments")
        return fromJson(responseBody, SwiftSchemeSubmitAck::class.java)
    }
}
