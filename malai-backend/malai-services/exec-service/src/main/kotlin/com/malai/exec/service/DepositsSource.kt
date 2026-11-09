package com.malai.exec.service

import com.malai.client.malai.payments.metrics.MalaiPaymentsMetricsClient
import com.malai.client.malai.payments.model.DepositsTotalResponse
import com.malai.exec.model.MetricsWindowRange
import org.springframework.stereotype.Component

/**
 * Deposits: money that entered the bank from outside during the asked window, read from the context
 * that owns the payment rows. payments-service decides what counts as a Deposit — an inbound,
 * settled credit into a Malai account, GBP only — so this source carries the window across and
 * surfaces whatever figure comes back, currency included.
 */
@Component
class DepositsSource(
    private val paymentsMetricsClient: MalaiPaymentsMetricsClient,
    private val execServiceTokenClient: ExecServiceTokenClient,
) {

    fun depositsTotal(window: MetricsWindowRange): DepositsTotalResponse =
        paymentsMetricsClient.depositsTotal(execServiceTokenClient.getToken(), window.from, window.until)
}
