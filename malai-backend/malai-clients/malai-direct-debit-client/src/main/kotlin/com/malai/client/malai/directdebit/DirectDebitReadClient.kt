package com.malai.client.malai.directdebit

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.directdebit.model.ActiveDirectDebitsResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Service-to-service read of an account's ACTIVE direct-debit mandates against
 * direct-debit-service's internal endpoint, used by account-service when composing the balance
 * forecast. Twin of `StandingOrderReadClient`; separate from `DirectDebitLodgementClient` so the
 * forecast read path and the sandbox lodge write path stay independent.
 */
@Component
class DirectDebitReadClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.direct-debit.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun getActiveDirectDebits(serviceToken: String, accountId: UUID): ActiveDirectDebitsResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/direct-debits?accountId=$accountId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/direct-debits")
        return fromJson(responseBody, ActiveDirectDebitsResponse::class.java)
    }
}
