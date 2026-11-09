package com.malai.exec.service

import com.malai.commons.tracking.MalaiSourceContext
import com.malai.commons.tracking.TestIdContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Reads each Metric's source on its own thread, under its own timeout.
 *
 * "The exec dashboard renders in bounded time, always" is a product promise, so a source that is
 * slow or unreachable degrades its own Metric to Unavailable and costs the response nothing. Legs
 * are started before any is awaited, so their timeouts run concurrently and the whole read is
 * bounded by the slowest single timeout rather than their sum.
 *
 * Every failure mode collapses to the same answer deliberately — a refused connection, a 500, a
 * malformed body and a timeout are all "this number is not known right now", and the caller has no
 * different action to take for any of them. The cause is logged rather than returned.
 *
 * The request thread's [MalaiSourceContext] and [TestIdContext] are captured and re-established on
 * each worker, so a leg's outbound call is still attributed to `exec-service` (the `X-Malai-Source`
 * stamp and the diagram lane) rather than being orphaned the way an out-of-band scheduler thread's
 * calls are.
 */
@Component
class MetricFanOut(
    private val executor: ExecutorService,
    @Value("\${malai.exec.metric-timeout-ms}") private val timeoutMs: Long,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun <T : Any> begin(name: String, read: () -> T): PendingMetric<T> {
        val source = MalaiSourceContext.valueOrNull
        val testId = TestIdContext.valueOrNull
        val future = executor.submit<T> {
            source?.let { MalaiSourceContext.value = it }
            testId?.let { TestIdContext.value = it }
            try {
                read()
            } finally {
                MalaiSourceContext.unset()
                TestIdContext.unset()
            }
        }
        return PendingMetric(name, future, timeoutMs, log)
    }
}

class PendingMetric<T : Any>(
    private val name: String,
    private val future: Future<T>,
    private val timeoutMs: Long,
    private val log: org.slf4j.Logger,
) {

    fun valueOrNull(): T? =
        try {
            future.get(timeoutMs, MILLISECONDS)
        } catch (ex: Exception) {
            future.cancel(true)
            log.warn("exec metric '{}' is unavailable for this read", name, ex)
            null
        }
}
