package com.malai.client.malai.directdebit

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.directdebit.model.LodgeMandateRequest
import com.malai.client.malai.directdebit.model.LodgedMandateResponse
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Service-to-service mandate lodgement against direct-debit-service's internal endpoint.
 * Used by sandbox-service so a demo driver can lodge a creditor-originated mandate bank-side,
 * the way a real creditor would — there is no customer-facing "create direct debit" surface.
 *
 * The receiving endpoint is authenticated via [com.malai.security.ServiceJwtFilter]; token
 * acquisition + caching is the caller's responsibility (see `ServiceTokenClient`).
 */
@Component
class DirectDebitLodgementClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.direct-debit.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun lodge(serviceToken: String, request: LodgeMandateRequest): LodgedMandateResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/direct-debits")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 201)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/direct-debits")
        return fromJson(responseBody, LodgedMandateResponse::class.java)
    }
}
