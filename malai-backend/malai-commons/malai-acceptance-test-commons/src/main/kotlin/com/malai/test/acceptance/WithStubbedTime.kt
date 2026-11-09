package com.malai.test.acceptance

import com.malai.commons.TimeService
import com.malai.test.commons.support.StubbedTimeService
import dev.kensa.Action
import dev.kensa.GivensContext
import java.time.Instant

/**
 * Acceptance-test helper for controlling `now()`. Resolves the [StubbedTimeService] registered by
 * `MalaiServiceTestConfiguration` (which activates under the `acceptance-tests` profile) directly
 * from the running service's Spring context so we do not depend on `@SpringBootTest`
 * infrastructure.
 */
interface WithStubbedTime : WithApplicationContext {

    fun stubbedTimeService(): StubbedTimeService =
        getBean(TimeService::class.java) as StubbedTimeService

    fun nowIs(instant: Instant) = Action<GivensContext> {
        stubbedTimeService().givenNowIs(instant)
    }

    fun nowIs(isoInstant: String) = nowIs(Instant.parse(isoInstant))
}
