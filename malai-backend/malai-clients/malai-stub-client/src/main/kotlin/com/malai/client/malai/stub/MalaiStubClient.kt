package com.malai.client.malai.stub

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.stub.card.model.CardMerchantsResponse
import com.malai.client.malai.stub.card.model.CardPurchaseForwardPayload
import com.malai.client.malai.stub.card.model.CardPurchaseResult
import com.malai.client.malai.stub.card.model.CardRefundForwardPayload
import com.malai.client.malai.stub.card.model.PresentNowResult
import com.malai.client.malai.stub.collection.model.CollectionForwardPayload
import com.malai.client.malai.stub.directory.model.CounterpartiesResponse
import com.malai.client.malai.stub.inbound.model.InboundPaymentForwardPayload
import com.malai.client.malai.stub.inbound.model.SandboxRail
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MalaiStubClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.stub.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun getCounterparties(): CounterpartiesResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/scenarios/counterparties")
        httpRequest.addHeader("Accept", "application/json")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /scenarios/counterparties")
        return fromJson(responseBody, CounterpartiesResponse::class.java)
    }

    fun postInbound(rail: SandboxRail, payload: InboundPaymentForwardPayload) {
        val path = when (rail) {
            SandboxRail.FPS -> "/scenarios/inbound/fps"
            SandboxRail.BACS -> "/scenarios/inbound/bacs"
        }
        val httpRequest = HttpPost("$malaiBaseUrl$path")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(payload))
        makeRequest(httpRequest, 204)
    }

    fun postCollectionBacs(payload: CollectionForwardPayload) {
        val httpRequest = HttpPost("$malaiBaseUrl/scenarios/collections/bacs")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(payload))
        makeRequest(httpRequest, 204)
    }

    fun getCardMerchants(): CardMerchantsResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/scenarios/cards/merchants")
        httpRequest.addHeader("Accept", "application/json")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /scenarios/cards/merchants")
        return fromJson(responseBody, CardMerchantsResponse::class.java)
    }

    fun triggerCardPurchase(payload: CardPurchaseForwardPayload): CardPurchaseResult {
        val httpRequest = HttpPost("$malaiBaseUrl/scenarios/cards/purchase")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(payload))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /scenarios/cards/purchase")
        return fromJson(responseBody, CardPurchaseResult::class.java)
    }

    fun presentPendingCards(): PresentNowResult {
        val httpRequest = HttpPost("$malaiBaseUrl/scenarios/cards/present-now")
        httpRequest.addHeader("Accept", "application/json")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /scenarios/cards/present-now")
        return fromJson(responseBody, PresentNowResult::class.java)
    }

    fun triggerCardRefund(payload: CardRefundForwardPayload) {
        val httpRequest = HttpPost("$malaiBaseUrl/scenarios/cards/refund")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(payload))
        makeRequest(httpRequest, 202)
    }

    fun registerCardPan(pan: String) {
        val httpRequest = HttpPost("$malaiBaseUrl/scenarios/cards/pans")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(RegisterCardPanPayload(pan)))
        makeRequest(httpRequest, 202)
    }
}

data class RegisterCardPanPayload(val pan: String)
