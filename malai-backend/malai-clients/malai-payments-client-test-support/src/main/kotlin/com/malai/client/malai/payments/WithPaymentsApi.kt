package com.malai.client.malai.payments

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.TestBuilder
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder
import com.malai.client.malai.payments.builders.response.SettledPaymentsResponseBuilder
import com.malai.commons.exception.MalaiCode
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

interface WithPaymentsApi : WithWiremock {

    fun initiatePaymentReturns(response: TestBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun initiatePaymentReturnsErrorStatus(status: Int, body: String) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )
    }

    fun initiateScheduledPaymentReturns(response: TestBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun initiateScheduledPaymentReturnsErrorStatus(status: Int, body: String) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)
                )
        )
    }

    fun getPaymentReturns(paymentId: UUID, response: InternalPaymentResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/payments/$paymentId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun getPaymentReturnsNotFound(paymentId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/payments/$paymentId"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "${MalaiCode.PAYMENT_NOT_FOUND.code}"}""")
                )
        )
    }

    fun settledPaymentsReturns(response: SettledPaymentsResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/payments/settled"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun settledPaymentsFailsWithServerError() = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/payments/settled"))
                .willReturn(aResponse().withStatus(500))
        )
    }

    companion object {
        const val CONTEXT_PATH = "/payments-service"
    }
}
