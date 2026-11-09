package com.malai.stub.scheme

import com.malai.client.malai.AbstractMalaiClient
import com.malai.scheme.contracts.bacs.BacsSchemeDirectCredit
import com.malai.scheme.contracts.bacs.BacsSchemeDirectDebitCollection
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StubBacsGatewayClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.bacs-gateway.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun notifyInbound(credit: BacsSchemeDirectCredit) {
        val httpRequest = HttpPost("$malaiBaseUrl/bacs/callbacks/inbound")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(credit))
        makeRequest(httpRequest, 202)
    }

    fun notifyCollection(collection: BacsSchemeDirectDebitCollection) {
        val httpRequest = HttpPost("$malaiBaseUrl/bacs/callbacks/collection")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(collection))
        makeRequest(httpRequest, 202)
    }
}
