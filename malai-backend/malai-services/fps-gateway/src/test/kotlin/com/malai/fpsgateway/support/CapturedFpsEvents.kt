package com.malai.fpsgateway.support

import com.malai.payments.messages.FpsInboundNotify
import com.malai.payments.messages.FpsOutboundReturned
import com.malai.payments.messages.FpsOutboundSettled
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class CapturedFpsEvents {

    private val settledByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<FpsOutboundSettled>>()
    private val returnedByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<FpsOutboundReturned>>()
    private val receivedByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<FpsInboundNotify>>()

    fun recordSettled(testId: String, event: FpsOutboundSettled) {
        settledByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun recordReturned(testId: String, event: FpsOutboundReturned) {
        returnedByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun recordReceived(testId: String, event: FpsInboundNotify) {
        receivedByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun settledFor(testId: String): List<FpsOutboundSettled> = settledByTestId[testId]?.toList() ?: emptyList()

    fun returnedFor(testId: String): List<FpsOutboundReturned> = returnedByTestId[testId]?.toList() ?: emptyList()

    fun receivedFor(testId: String): List<FpsInboundNotify> = receivedByTestId[testId]?.toList() ?: emptyList()
}
