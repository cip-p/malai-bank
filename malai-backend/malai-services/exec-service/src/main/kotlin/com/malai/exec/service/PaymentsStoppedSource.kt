package com.malai.exec.service

import com.malai.client.malai.payments.metrics.MalaiPaymentsMetricsClient
import com.malai.client.malai.payments.model.PaymentsStoppedTotalResponse
import com.malai.exec.model.MetricsWindowRange
import org.springframework.stereotype.Component

/**
 * Payments stopped: money the bank refused after Screening review during the asked window, read from
 * the context that owns the payment rows. payments-service decides what counts — Payments in the
 * `DECLINED` state, the enacted result of an adverse ruling, GBP only — so this source carries the
 * window across and surfaces whatever figure comes back, currency included.
 *
 * Sourced from the payment state rather than the MDR: an *edit* to a DECLINE declines the payment and
 * so counts, a *reject* leaves it parked in `REFERRED` and does not. Deliberately not "fraud
 * prevented" — a declined payment is money the bank stopped, not proven fraud.
 */
@Component
class PaymentsStoppedSource(
    private val paymentsMetricsClient: MalaiPaymentsMetricsClient,
    private val execServiceTokenClient: ExecServiceTokenClient,
) {

    fun paymentsStoppedTotal(window: MetricsWindowRange): PaymentsStoppedTotalResponse =
        paymentsMetricsClient.paymentsStoppedTotal(execServiceTokenClient.getToken(), window.from, window.until)
}
