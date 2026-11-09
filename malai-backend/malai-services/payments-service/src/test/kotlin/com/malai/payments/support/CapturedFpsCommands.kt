package com.malai.payments.support

import com.malai.payments.messages.FpsOutboundSubmit
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object CapturedFpsCommands {

    private val byTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<FpsOutboundSubmit>>()

    fun record(testId: String, submit: FpsOutboundSubmit) {
        byTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(submit)
    }

    fun clear() = byTestId.clear()

    fun awaitForTestId(testId: String, timeout: Duration = Duration.ofSeconds(5)): List<FpsOutboundSubmit> {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val current = byTestId[testId]?.toList().orEmpty()
            if (current.isNotEmpty()) return current
            Thread.sleep(50)
        }
        return byTestId[testId]?.toList().orEmpty()
    }
}
