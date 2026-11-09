package com.malai.stub.scheme

import com.malai.client.malai.AbstractMalaiClient
import com.malai.scheme.contracts.swift.SwiftSchemeSettlementCallback
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StubSwiftGatewayClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.swift-gateway.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun notifySettled(callback: SwiftSchemeSettlementCallback) {
        val httpRequest = HttpPost("$malaiBaseUrl/swift/callbacks/settlement")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(callback))
        makeRequest(httpRequest, 202)
    }
}
