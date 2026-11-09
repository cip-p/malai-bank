package com.malai.client.malai.payments.metrics

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.payments.builders.response.DepositsTotalResponseBuilder
import com.malai.client.malai.payments.builders.response.PaymentsStoppedTotalResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext

/**
 * WireMock stubs for payments-service's service-to-service metric reads (Deposits, Payments stopped).
 *
 * The stubs match on path only, not on the window, so a test that cares about the window asserts on
 * the captured request rather than encoding it twice.
 */
interface WithPaymentsMetricsApi : WithWiremock {

    fun depositsTotalReturns(response: DepositsTotalResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(DEPOSITS_TOTAL_PATH))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun depositsTotalFailsWithServerError() = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(DEPOSITS_TOTAL_PATH))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun depositsTotalTakesLongerThan(delayMillis: Int, response: DepositsTotalResponseBuilder) =
        Action<GivensContext> {
            wiremock().stubFor(
                get(urlPathEqualTo(DEPOSITS_TOTAL_PATH))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(response.build())
                            .withFixedDelay(delayMillis)
                    )
            )
        }

    fun paymentsStoppedTotalReturns(response: PaymentsStoppedTotalResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(PAYMENTS_STOPPED_TOTAL_PATH))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun paymentsStoppedTotalFailsWithServerError() = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(PAYMENTS_STOPPED_TOTAL_PATH))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun paymentsStoppedTotalTakesLongerThan(delayMillis: Int, response: PaymentsStoppedTotalResponseBuilder) =
        Action<GivensContext> {
            wiremock().stubFor(
                get(urlPathEqualTo(PAYMENTS_STOPPED_TOTAL_PATH))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(response.build())
                            .withFixedDelay(delayMillis)
                    )
            )
        }

    companion object {
        const val CONTEXT_PATH = "/payments-service"
        const val DEPOSITS_TOTAL_PATH = "$CONTEXT_PATH/api/v1/internal/payments/deposits-total"
        const val PAYMENTS_STOPPED_TOTAL_PATH = "$CONTEXT_PATH/api/v1/internal/payments/stopped-total"
    }
}
