package com.malai.swiftgateway.support

import com.malai.payments.messages.SwiftOutboundSettled
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class CapturedSwiftEvents {

    private val settledByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<SwiftOutboundSettled>>()

    fun recordSettled(testId: String, event: SwiftOutboundSettled) {
        settledByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun settledFor(testId: String): List<SwiftOutboundSettled> = settledByTestId[testId]?.toList() ?: emptyList()
}
