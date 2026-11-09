package com.malai.client.malai.card.metrics

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.card.model.CardsIssuedResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.net.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class CardMetricsClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.card.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun cardsIssued(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ): CardsIssuedResponse {
        val uri = URIBuilder("$malaiBaseUrl/api/v1/internal/cards/issued-count")
            .addParameter("from", from.toString())
            .addParameter("until", until.toString())
            .build()

        val httpRequest = HttpGet(uri)
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/cards/issued-count")
        return fromJson(responseBody, CardsIssuedResponse::class.java)
    }
}
