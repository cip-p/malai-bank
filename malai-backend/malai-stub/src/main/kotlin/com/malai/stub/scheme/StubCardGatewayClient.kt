package com.malai.stub.scheme

import com.malai.client.malai.AbstractMalaiClient
import com.malai.scheme.contracts.card.CardSchemeAuthorisationRequest
import com.malai.scheme.contracts.card.CardSchemeAuthorisationResponse
import com.malai.scheme.contracts.card.CardSchemePresentmentRequest
import com.malai.scheme.contracts.card.CardSchemeRefundRequest
import com.malai.scheme.contracts.card.CardSchemeReversalRequest
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StubCardGatewayClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.card-gateway.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun authorise(request: CardSchemeAuthorisationRequest): CardSchemeAuthorisationResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/cards/callbacks/authorise")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected an authorisation response body from POST /cards/callbacks/authorise")
        return fromJson(responseBody, CardSchemeAuthorisationResponse::class.java)
    }

    fun present(request: CardSchemePresentmentRequest) {
        val httpRequest = HttpPost("$malaiBaseUrl/cards/callbacks/present")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))
        makeRequest(httpRequest, 202)
    }

    fun reverse(request: CardSchemeReversalRequest) {
        val httpRequest = HttpPost("$malaiBaseUrl/cards/callbacks/reverse")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))
        makeRequest(httpRequest, 202)
    }

    fun refund(request: CardSchemeRefundRequest) {
        val httpRequest = HttpPost("$malaiBaseUrl/cards/callbacks/refund")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))
        makeRequest(httpRequest, 202)
    }
}
