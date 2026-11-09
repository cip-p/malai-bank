package com.malai.test.acceptance

import org.awaitility.Awaitility
import java.time.Duration

fun eventually(
    timeout: Duration = Duration.ofSeconds(15),
    pollInterval: Duration = Duration.ofMillis(100),
    assertion: () -> Unit,
) {
    Awaitility.await()
        .atMost(timeout)
        .pollInterval(pollInterval)
        .ignoreExceptions()
        .untilAsserted(assertion)
}
