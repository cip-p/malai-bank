package com.malai.commons

import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TimeService {

    fun now(): Instant {
        return Instant.now()
    }
}
