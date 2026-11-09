package com.malai.test.acceptance.kensa

import com.malai.commons.tracking.TestId
import dev.kensa.state.CapturedInteractions
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-test-id store of [CapturedInteractions] that Kensa uses to render sequence diagrams.
 *
 * The shared [com.malai.test.acceptance.kensa.interaction.MalaiHttpInteractionCapturer] and
 * [com.malai.test.acceptance.kensa.interaction.WireMockInteractionCapturer] both look up the
 * current test's `CapturedInteractions` via this holder (keyed by [TestId]), so we can correlate
 * captured HTTP traffic back to the test that produced it.
 */
object CapturedInteractionHolder {

    private val interactions = ConcurrentHashMap<TestId, CapturedInteractions>()

    operator fun get(testId: TestId) = interactions[testId]
    operator fun set(testId: TestId, interactions: CapturedInteractions) {
        this.interactions[testId] = interactions
    }

    fun remove(testId: TestId) = interactions.remove(testId)
}
