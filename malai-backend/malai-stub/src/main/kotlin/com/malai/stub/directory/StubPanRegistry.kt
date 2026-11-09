package com.malai.stub.directory

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArraySet

@Component
class StubPanRegistry {

    private val pans = CopyOnWriteArraySet<String>()

    fun register(pan: String) {
        pans.add(pan)
    }

    fun contains(pan: String): Boolean = pans.contains(pan)

    fun all(): List<String> = pans.toList()
}
