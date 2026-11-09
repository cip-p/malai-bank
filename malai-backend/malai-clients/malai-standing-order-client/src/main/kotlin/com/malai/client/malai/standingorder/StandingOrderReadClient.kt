package com.malai.client.malai.standingorder

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.standingorder.model.ActiveStandingOrdersResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StandingOrderReadClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.standing-order.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun getActiveStandingOrders(serviceToken: String, accountId: UUID): ActiveStandingOrdersResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/standing-orders?accountId=$accountId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/standing-orders")
        return fromJson(responseBody, ActiveStandingOrdersResponse::class.java)
    }
}
