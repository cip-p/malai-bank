package com.malai.exec.service

import com.malai.commons.TimeService
import com.malai.exec.model.CountMetric
import com.malai.exec.model.ExecMetricsResponse
import com.malai.exec.model.MetricsWindow
import com.malai.exec.model.MetricsWindowRange
import com.malai.exec.model.MoneyMetric
import org.springframework.stereotype.Service

/**
 * Composes a window's Metrics. Every leg is started before any is awaited, so the sources
 * are read concurrently and one slow context delays nothing but its own tile. Each Metric is one
 * leg here, and one slow or broken source degrades its own tile to Unavailable without touching the
 * other three — the availability mechanism the surface already binds to.
 */
@Service
class ExecMetricsService(
    private val timeService: TimeService,
    private val fanOut: MetricFanOut,
    private val newCustomersSource: NewCustomersSource,
    private val depositsSource: DepositsSource,
    private val cardsIssuedSource: CardsIssuedSource,
    private val paymentsStoppedSource: PaymentsStoppedSource,
) {

    fun metrics(window: MetricsWindow): ExecMetricsResponse {
        val asOf = timeService.now()
        val range = MetricsWindowRange.resolve(window, asOf)

        val newCustomers = fanOut.begin("newCustomers") { newCustomersSource.count(range) }
        val deposits = fanOut.begin("deposits") { depositsSource.depositsTotal(range) }
        val cardsIssued = fanOut.begin("cardsIssued") { cardsIssuedSource.count(range) }
        val paymentsStopped = fanOut.begin("paymentsStopped") { paymentsStoppedSource.paymentsStoppedTotal(range) }

        return ExecMetricsResponse(
            window = range.window,
            windowStart = range.windowStart,
            bankingDay = range.bankingDay,
            asOf = asOf,
            newCustomers = newCustomers.valueOrNull()?.let(CountMetric::available) ?: CountMetric.unavailable(),
            deposits = deposits.valueOrNull()
                ?.let { MoneyMetric.available(it.amountMinorUnits, it.currency) }
                ?: MoneyMetric.unavailable(),
            cardsIssued = cardsIssued.valueOrNull()?.let(CountMetric::available) ?: CountMetric.unavailable(),
            paymentsStopped = paymentsStopped.valueOrNull()
                ?.let { MoneyMetric.available(it.amountMinorUnits, it.currency) }
                ?: MoneyMetric.unavailable(),
        )
    }
}
