package com.malai.client.malai.payments.metrics

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.payments.model.DepositsTotalResponse
import com.malai.client.malai.payments.model.PaymentsStoppedTotalResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.net.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Calls payments-service's service-to-service Deposits metric read.
 *
 * The window is passed as two instants because the payments context has no opinion about what a day
 * is — the composing caller (exec-service) decides that and this client only carries it across.
 */
@Component
class MalaiPaymentsMetricsClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.payments.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun depositsTotal(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ): DepositsTotalResponse {
        val uri = URIBuilder("$malaiBaseUrl/api/v1/internal/payments/deposits-total")
            .addParameter("from", from.toString())
            .addParameter("until", until.toString())
            .build()

        val httpRequest = HttpGet(uri)
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/payments/deposits-total")
        return fromJson(responseBody, DepositsTotalResponse::class.java)
    }

    fun paymentsStoppedTotal(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ): PaymentsStoppedTotalResponse {
        val uri = URIBuilder("$malaiBaseUrl/api/v1/internal/payments/stopped-total")
            .addParameter("from", from.toString())
            .addParameter("until", until.toString())
            .build()

        val httpRequest = HttpGet(uri)
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/payments/stopped-total")
        return fromJson(responseBody, PaymentsStoppedTotalResponse::class.java)
    }
}
