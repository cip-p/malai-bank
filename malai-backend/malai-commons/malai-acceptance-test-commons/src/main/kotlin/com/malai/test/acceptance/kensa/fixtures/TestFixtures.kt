package com.malai.test.acceptance.kensa.fixtures

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*
import kotlin.time.Duration

object TestFixtures {

    fun aString(block: StringBuilder.() -> Unit = {}): String = StringBuilder().apply(block).build()
    fun anInteger(block: IntBuilder.() -> Unit = {}) = IntBuilder().apply(block).build()
    fun aLong(block: LongBuilder.() -> Unit = {}) = LongBuilder().apply(block).build()
    fun aDouble(block: DoubleBuilder.() -> Unit = {}) = DoubleBuilder().apply(block).build()
    fun aBoolean(): Boolean = oneOf(true, false)

    fun anInstantInTheNext(
        duration: Duration,
        clock: Clock = Clock.systemUTC(),
        truncatedTo: ChronoUnit = MILLIS
    ): Instant =
        clock.instant().plusSeconds(secondsWithin(duration)).truncatedTo(truncatedTo)

    fun anInstantInTheLast(
        duration: Duration,
        clock: Clock = Clock.systemUTC(),
        truncatedTo: ChronoUnit = MILLIS
    ): Instant =
        clock.instant().minusSeconds(secondsWithin(duration)).truncatedTo(truncatedTo)

    private fun secondsWithin(duration: Duration): Long = aLong { inRange(1..duration.inWholeSeconds) }

    fun oneOf(range: IntRange): Int = range.random()
    inline fun <reified T : Any?> oneOf(vararg values: T): T = values.random()

    fun someRandomGroupId() = someRandomUuid()
    fun someRandomNumberString(withLength: Int = 6): String =
        generateSequence(('1'..'9').random()) { ('0'..'9').random() }
            .take(withLength)
            .joinToString(separator = "")
    fun someRandomPopName(popName: String = "MIFW1") = "$popName/${someRandomNumberString(3)}/${someRandomNumberString(3)}"
    fun someRandomPremisesId() = "01_002_002030_${someRandomNumberString(10)}_1"
    fun someRandomServiceId() = someRandomUuid()
    fun someRandomUuid() = UUID.randomUUID().toString()
}
