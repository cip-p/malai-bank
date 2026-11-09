package com.malai.aml.sar

import com.malai.aml.monitoring.AmlProperties
import com.malai.commons.tracking.MalaiSourceContext
import com.malai.commons.tracking.TestIdContext
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException

@Component
class SarDraftBudget(
    private val properties: AmlProperties,
) {

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "aml-sar-drafter").apply { isDaemon = true }
    }

    fun <T : Any> within(draft: () -> T): T {
        val source = MalaiSourceContext.valueOrNull
        val testId = TestIdContext.valueOrNull
        val pending = executor.submit<T> {
            source?.let { MalaiSourceContext.value = it }
            testId?.let { TestIdContext.value = it }
            try {
                draft()
            } finally {
                MalaiSourceContext.unset()
                TestIdContext.unset()
            }
        }

        return try {
            pending.get(properties.sar.deciderTimeoutMs, MILLISECONDS)
        } catch (ex: TimeoutException) {
            pending.cancel(true)
            throw SarDraftTimedOutException("the drafter exceeded its ${properties.sar.deciderTimeoutMs}ms budget")
        } catch (ex: ExecutionException) {
            throw ex.cause ?: ex
        }
    }

    @PreDestroy
    fun abandonInvocationsOnShutdown() {
        executor.shutdownNow()
    }
}
