package com.malai.test.commons.support

import com.malai.commons.TimeService
import java.time.Instant

class StubbedTimeService : TimeService() {

    private var stubbedNow: Instant? = null

    override fun now(): Instant {
        return if (stubbedNow == null) {
            Instant.now()
        } else {
            stubbedNow!!
        }
    }

    fun givenNowIs(now: Instant) {
        stubbedNow = now
    }

    fun reset() {
        stubbedNow = null
    }

}
