package com.malai.payments.support

import com.malai.payments.messages.BacsCollectionReturn
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object CapturedBacsCollectionReturns {

    private val byTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<BacsCollectionReturn>>()

    fun record(testId: String, returned: BacsCollectionReturn) {
        byTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(returned)
    }

    fun clear() = byTestId.clear()

    fun awaitForTestId(testId: String, timeout: Duration = Duration.ofSeconds(3)): List<BacsCollectionReturn> {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val current = byTestId[testId]?.toList().orEmpty()
            if (current.isNotEmpty()) return current
            Thread.sleep(50)
        }
        return byTestId[testId]?.toList().orEmpty()
    }
}
