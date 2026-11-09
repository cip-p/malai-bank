package com.malai.cardgateway.support

import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.messages.CardRefundNotify
import com.malai.card.messages.CardReversalNotify
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class CapturedCardEvents {

    private val presentedByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<CardPresentmentNotify>>()
    private val reversedByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<CardReversalNotify>>()
    private val refundedByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<CardRefundNotify>>()

    fun recordPresented(testId: String, event: CardPresentmentNotify) {
        presentedByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun recordReversed(testId: String, event: CardReversalNotify) {
        reversedByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun recordRefunded(testId: String, event: CardRefundNotify) {
        refundedByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun presentedFor(testId: String): List<CardPresentmentNotify> = presentedByTestId[testId]?.toList() ?: emptyList()

    fun reversedFor(testId: String): List<CardReversalNotify> = reversedByTestId[testId]?.toList() ?: emptyList()

    fun refundedFor(testId: String): List<CardRefundNotify> = refundedByTestId[testId]?.toList() ?: emptyList()
}
