package com.malai.test.e2e.fixtures

import dev.kensa.RenderedValue
import java.util.*

object FixtureCapture {

    private var capturedFixtures: MutableMap<CaptureType, Any> = mutableMapOf()

    fun reset() {
        capturedFixtures.clear()
    }

    fun <T> capture(captureType: CaptureType, capturedValue: T): T {
        capturedFixtures[captureType] = capturedValue as Any
        return capturedValue
    }

    @RenderedValue
    fun captured(captureType: CaptureType): String = capturedFixtures[captureType]!!.toString()

    @RenderedValue
    fun capturedUuid(captureType: CaptureType): UUID = capturedFixtures[captureType]!! as UUID
}

enum class CaptureType {
    USER_ID,
    USER_EMAIL,
    ACCOUNT_HOLDER_ID,
    ACCOUNT_ID
}
