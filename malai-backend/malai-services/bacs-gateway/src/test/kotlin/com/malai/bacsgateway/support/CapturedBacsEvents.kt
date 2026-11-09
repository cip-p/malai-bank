package com.malai.bacsgateway.support

import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.messages.BacsInboundNotify
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class CapturedBacsEvents {

    private val receivedByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<BacsInboundNotify>>()
    private val collectionByTestId = ConcurrentHashMap<String, CopyOnWriteArrayList<BacsCollectionNotify>>()

    fun recordReceived(testId: String, event: BacsInboundNotify) {
        receivedByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun receivedFor(testId: String): List<BacsInboundNotify> = receivedByTestId[testId]?.toList() ?: emptyList()

    fun recordCollection(testId: String, event: BacsCollectionNotify) {
        collectionByTestId.getOrPut(testId) { CopyOnWriteArrayList() }.add(event)
    }

    fun collectionFor(testId: String): List<BacsCollectionNotify> = collectionByTestId[testId]?.toList() ?: emptyList()
}
