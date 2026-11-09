package com.malai.exec.model

import com.malai.commons.time.BankingDay
import java.time.Instant
import java.time.LocalDate

enum class MetricStatus { AVAILABLE, UNAVAILABLE }

/**
 * The trailing windows the dashboard reports over. Every window is anchored to London midnight —
 * never rolling from the current second — so a figure is stable within a Banking day, and every
 * window ends at the moment of the read: a wider window includes today so far, not a closed past
 * period. TODAY is the Banking day itself and the endpoint's default.
 */
enum class MetricsWindow {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_12_MONTHS,
}

/**
 * A MetricsWindow resolved against a moment: the first and last Banking days it covers, and the
 * half-open instant range the sources are asked for — `from` at windowStart's London midnight,
 * `until` at the end of the current Banking day, exactly as the single-day window always worked.
 */
data class MetricsWindowRange(
    val window: MetricsWindow,
    val windowStart: LocalDate,
    val bankingDay: LocalDate,
    val from: Instant,
    val until: Instant,
) {
    companion object {
        fun resolve(window: MetricsWindow, asOf: Instant): MetricsWindowRange {
            val day = BankingDay.containing(asOf)
            val windowStart = when (window) {
                MetricsWindow.TODAY -> day.date
                MetricsWindow.LAST_7_DAYS -> day.date.minusDays(6)
                MetricsWindow.LAST_30_DAYS -> day.date.minusDays(29)
                MetricsWindow.LAST_12_MONTHS -> day.date.minusMonths(12)
            }
            return MetricsWindowRange(
                window = window,
                windowStart = windowStart,
                bankingDay = day.date,
                from = windowStart.atStartOfDay(BankingDay.ZONE).toInstant(),
                until = day.until,
            )
        }
    }
}

/**
 * A Metric is either valued or Unavailable — never a zero standing in for a number the platform
 * could not obtain. Absent fields are dropped from the payload by the service's non-null Jackson
 * inclusion, so an UNAVAILABLE Metric serialises as `{"status":"UNAVAILABLE"}` and carries no
 * reason: a client can act on none, and naming the sick source would leak internal topology.
 */
data class CountMetric(
    val status: MetricStatus,
    val count: Long? = null,
) {
    companion object {
        fun available(count: Long) = CountMetric(MetricStatus.AVAILABLE, count)
        fun unavailable() = CountMetric(MetricStatus.UNAVAILABLE)
    }
}

data class MoneyMetric(
    val status: MetricStatus,
    val amountMinorUnits: Long? = null,
    val currency: String? = null,
) {
    companion object {
        fun available(amountMinorUnits: Long, currency: String) =
            MoneyMetric(MetricStatus.AVAILABLE, amountMinorUnits, currency)

        fun unavailable() = MoneyMetric(MetricStatus.UNAVAILABLE)
    }
}

data class ExecMetricsResponse(
    val window: MetricsWindow,
    val windowStart: LocalDate,
    val bankingDay: LocalDate,
    val asOf: Instant,
    val newCustomers: CountMetric,
    val deposits: MoneyMetric,
    val cardsIssued: CountMetric,
    val paymentsStopped: MoneyMetric,
)
