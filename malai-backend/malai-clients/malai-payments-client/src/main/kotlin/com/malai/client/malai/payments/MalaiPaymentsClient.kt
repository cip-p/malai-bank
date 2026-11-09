package com.malai.client.malai.payments

import tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.payments.model.InitiatePaymentRequest
import com.malai.client.malai.payments.model.InitiatePaymentResponse
import com.malai.client.malai.payments.model.InternalInitiatePaymentRequest
import com.malai.client.malai.payments.model.InternalPaymentResponse
import com.malai.client.malai.payments.model.SettledPaymentsResponse
import com.malai.commons.exception.MalaiCode
import com.malai.security.SecurityConfig.Companion.ACCOUNT_HOLDER_ID_HEADER
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.net.URIBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import tools.jackson.module.kotlin.jacksonMapperBuilder

@Component
class MalaiPaymentsClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.payments.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    private val errorBodyMapper = jacksonMapperBuilder().configure(FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    fun initiatePayment(
        accessToken: String,
        accountHolderId: UUID,
        idempotencyKey: String,
        request: InitiatePaymentRequest,
    ): InitiatePaymentResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/payments")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $accessToken")
        httpRequest.addHeader(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
        httpRequest.addHeader("Idempotency-Key", idempotencyKey)
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 201)
            ?: throw RuntimeException("expected a response body from POST /api/v1/payments")
        return fromJson(responseBody, InitiatePaymentResponse::class.java)
    }

    fun initiateScheduledPayment(
        serviceToken: String,
        request: InternalInitiatePaymentRequest,
    ): InitiatePaymentResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/payments")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(toJson(request))

        try {
            val responseBody = makeRequest(httpRequest, 201)
                ?: throw RuntimeException("expected a response body from POST /api/v1/internal/payments")
            return fromJson(responseBody, InitiatePaymentResponse::class.java)
        } catch (ex: MalaiClientResponseException) {
            if (isInsufficientFunds(ex.responseBody)) {
                throw PaymentInsufficientFundsException(ex.responseBody)
            }
            throw ex
        }
    }

    /**
     * The payment row as the bank records it, unscoped by account holder — the internal read a
     * caller reconstructing why something happened starts from.
     */
    fun getPayment(
        serviceToken: String,
        paymentId: UUID,
    ): InternalPaymentResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/payments/$paymentId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/payments/$paymentId")
        return fromJson(responseBody, InternalPaymentResponse::class.java)
    }

    fun settledPayments(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ): SettledPaymentsResponse {
        val uri = URIBuilder("$malaiBaseUrl/api/v1/internal/payments/settled")
            .addParameter("from", from.toString())
            .addParameter("until", until.toString())
            .build()

        val httpRequest = HttpGet(uri)
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/payments/settled")
        return fromJson(responseBody, SettledPaymentsResponse::class.java)
    }

    private fun isInsufficientFunds(responseBody: String?): Boolean {
        if (responseBody == null) return false
        return runCatching {
            errorBodyMapper.readValue(responseBody, ErrorBody::class.java).message
        }.getOrNull() == MalaiCode.PAYMENT_INSUFFICIENT_FUNDS.code.toString()
    }

    private data class ErrorBody(val message: String?)
}
