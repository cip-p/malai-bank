package com.malai.fpsgateway

import com.malai.client.malai.AbstractMalaiClient
import com.malai.scheme.contracts.fps.FpsSchemeReturnToSender
import com.malai.scheme.contracts.fps.FpsSchemeSubmitAck
import com.malai.scheme.contracts.fps.FpsSchemeSubmitRequest
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class FpsSchemeClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.fps-scheme.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun submit(request: FpsSchemeSubmitRequest): FpsSchemeSubmitAck {
        val httpRequest = HttpPost("$malaiBaseUrl/fps/payments")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 202)
            ?: throw RuntimeException("expected a 202 ack body from POST /fps/payments")
        return fromJson(responseBody, FpsSchemeSubmitAck::class.java)
    }

    fun returnToSender(request: FpsSchemeReturnToSender) {
        val httpRequest = HttpPost("$malaiBaseUrl/fps/returns")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))
        makeRequest(httpRequest, 202)
    }
}
