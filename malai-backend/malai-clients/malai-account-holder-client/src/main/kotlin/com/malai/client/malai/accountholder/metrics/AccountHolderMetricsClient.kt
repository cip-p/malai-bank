package com.malai.client.malai.accountholder.metrics

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.accountholder.model.AccountHoldersCreatedResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.net.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Calls account-holder-service's service-to-service metric read.
 *
 * The window is passed as two instants because the account-holder context has no opinion about
 * what a day is — the composing caller decides that and this client only carries it across.
 */
@Component
class AccountHolderMetricsClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.account-holder.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun accountHoldersCreated(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ): AccountHoldersCreatedResponse {
        val uri = URIBuilder("$malaiBaseUrl/api/v1/internal/account-holders/created-count")
            .addParameter("from", from.toString())
            .addParameter("until", until.toString())
            .build()

        val httpRequest = HttpGet(uri)
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/account-holders/created-count")
        return fromJson(responseBody, AccountHoldersCreatedResponse::class.java)
    }
}
