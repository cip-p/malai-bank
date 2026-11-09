package com.malai.copilot.service

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
class DeciderBudget(
    private val properties: CopilotProperties,
) {

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "copilot-decider").apply { isDaemon = true }
    }

    fun <T : Any> within(decide: () -> T): T {
        val source = MalaiSourceContext.valueOrNull
        val testId = TestIdContext.valueOrNull
        val pending = executor.submit<T> {
            source?.let { MalaiSourceContext.value = it }
            testId?.let { TestIdContext.value = it }
            try {
                decide()
            } finally {
                MalaiSourceContext.unset()
                TestIdContext.unset()
            }
        }

        return try {
            pending.get(properties.deciderTimeoutMs, MILLISECONDS)
        } catch (ex: TimeoutException) {
            pending.cancel(true)
            throw CopilotTimedOutException("the decider exceeded its ${properties.deciderTimeoutMs}ms budget")
        } catch (ex: ExecutionException) {
            throw ex.cause ?: ex
        }
    }

    @PreDestroy
    fun abandonInvocationsOnShutdown() {
        executor.shutdownNow()
    }
}
