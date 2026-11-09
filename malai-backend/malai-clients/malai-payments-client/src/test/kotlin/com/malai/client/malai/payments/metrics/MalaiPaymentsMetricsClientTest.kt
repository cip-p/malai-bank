package com.malai.client.malai.payments.metrics

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.payments.builders.response.DepositsTotalResponseBuilder.Companion.aDepositsTotalResponse
import com.malai.client.malai.payments.builders.response.PaymentsStoppedTotalResponseBuilder.Companion.aPaymentsStoppedTotalResponse
import com.malai.client.malai.payments.model.DepositsTotalResponse
import com.malai.client.malai.payments.model.PaymentsStoppedTotalResponse
import com.malai.test.acceptance.MalaiClientTest
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import java.time.Instant

@MalaiClientSpringTest
class MalaiPaymentsMetricsClientTest : MalaiClientTest(), WithPaymentsMetricsApi {

    @Autowired
    private lateinit var paymentsMetricsClient: MalaiPaymentsMetricsClient

    private var lastResponse: DepositsTotalResponse? = null
    private var lastPaymentsStoppedResponse: PaymentsStoppedTotalResponse? = null
    private var lastThrowable: Throwable? = null

    private val windowStart = Instant.parse("2026-07-19T23:00:00Z")
    private val windowEnd = Instant.parse("2026-07-20T23:00:00Z")

    @Test
    fun readsTheDepositsTotalForAWindow() {
        given(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_250_000)))

        whenever(clientCallsDepositsTotal("some-service-token", windowStart, windowEnd))

        then(theLastResponse()) {
            this shouldBe DepositsTotalResponse(currency = "GBP", amountMinorUnits = 1_250_000)
        }
        and(madeTheFollowingHttpRequest {
            url.startsWith("/payments-service/api/v1/internal/payments/deposits-total")
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            url shouldBe "/payments-service/api/v1/internal/payments/deposits-total" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun aQuietDayDeserialisesAsZeroRatherThanAnAbsentTotal() {
        given(depositsTotalReturns(aDepositsTotalResponse().withCurrency("GBP").withAmountMinorUnits(0)))

        whenever(clientCallsDepositsTotal("some-service-token", windowStart, windowEnd))

        then(theLastResponse()) {
            this shouldBe DepositsTotalResponse(currency = "GBP", amountMinorUnits = 0)
        }
    }

    @Test
    fun surfacesAMetricReadFailureAsAClientResponseException() {
        given(depositsTotalFailsWithServerError())

        whenever(clientAttemptsToReadDepositsTotal("some-service-token", windowStart, windowEnd))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    @Test
    fun readsThePaymentsStoppedTotalForAWindow() {
        given(paymentsStoppedTotalReturns(aPaymentsStoppedTotalResponse().withCurrency("GBP").withAmountMinorUnits(1_000_000)))

        whenever(clientCallsPaymentsStoppedTotal("some-service-token", windowStart, windowEnd))

        then(theLastPaymentsStoppedResponse()) {
            this shouldBe PaymentsStoppedTotalResponse(currency = "GBP", amountMinorUnits = 1_000_000)
        }
        and(madeTheFollowingHttpRequest {
            url.startsWith("/payments-service/api/v1/internal/payments/stopped-total")
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            url shouldBe "/payments-service/api/v1/internal/payments/stopped-total" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun surfacesAPaymentsStoppedReadFailureAsAClientResponseException() {
        given(paymentsStoppedTotalFailsWithServerError())

        whenever(clientAttemptsToReadPaymentsStoppedTotal("some-service-token", windowStart, windowEnd))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    private fun clientCallsDepositsTotal(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ) = Action<ActionContext> {
        lastResponse = paymentsMetricsClient.depositsTotal(serviceToken, from, until)
    }

    private fun clientCallsPaymentsStoppedTotal(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ) = Action<ActionContext> {
        lastPaymentsStoppedResponse = paymentsMetricsClient.paymentsStoppedTotal(serviceToken, from, until)
    }

    private fun clientAttemptsToReadPaymentsStoppedTotal(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ) = Action<ActionContext> {
        lastThrowable = runCatching {
            paymentsMetricsClient.paymentsStoppedTotal(serviceToken, from, until)
        }.exceptionOrNull()
    }

    private fun theLastPaymentsStoppedResponse() = StateCollector { _ ->
        lastPaymentsStoppedResponse ?: error("No payments-stopped-total response captured")
    }

    private fun clientAttemptsToReadDepositsTotal(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ) = Action<ActionContext> {
        lastThrowable = runCatching {
            paymentsMetricsClient.depositsTotal(serviceToken, from, until)
        }.exceptionOrNull()
    }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No deposits-total response captured")
    }

    private fun theLastThrowable() = StateCollector { _ ->
        lastThrowable ?: error("No throwable captured")
    }
}
