package com.malai.stub.scheme

import com.malai.client.malai.AbstractMalaiClient
import com.malai.scheme.contracts.fps.FpsSchemeInboundNotification
import com.malai.scheme.contracts.fps.FpsSchemeReturnCallback
import com.malai.scheme.contracts.fps.FpsSchemeSettlementCallback
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StubFpsGatewayClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.fps-gateway.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun notifySettled(callback: FpsSchemeSettlementCallback) {
        val httpRequest = HttpPost("$malaiBaseUrl/fps/callbacks/settlement")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(callback))
        makeRequest(httpRequest, 202)
    }

    fun notifyReturned(callback: FpsSchemeReturnCallback) {
        val httpRequest = HttpPost("$malaiBaseUrl/fps/callbacks/return")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(callback))
        makeRequest(httpRequest, 202)
    }

    fun notifyInbound(notification: FpsSchemeInboundNotification) {
        val httpRequest = HttpPost("$malaiBaseUrl/fps/callbacks/inbound")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(notification))
        makeRequest(httpRequest, 202)
    }
}
