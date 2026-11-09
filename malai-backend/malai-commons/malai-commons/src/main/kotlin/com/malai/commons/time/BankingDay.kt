package com.malai.commons.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The platform's Banking day: since midnight `Europe/London`. A UK bank's executive means the
 * London day, not the UTC one — in British Summer Time those differ by an hour, so a figure
 * windowed on UTC midnight would put an hour of the evening's activity on the wrong day.
 *
 * This is the codebase's first stated timezone convention and lives in commons deliberately: the
 * next day-scoped figure should adopt it rather than invent a second one. The *language* around
 * it — what a Banking day means for a Metric — belongs to `exec-service`'s CONTEXT.md; this is
 * only the mechanism.
 */
object BankingDay {

    val ZONE: ZoneId = ZoneId.of("Europe/London")

    fun containing(instant: Instant): BankingDayWindow {
        val date = instant.atZone(ZONE).toLocalDate()
        return BankingDayWindow(
            date = date,
            from = date.atStartOfDay(ZONE).toInstant(),
            until = date.plusDays(1).atStartOfDay(ZONE).toInstant(),
        )
    }
}

/**
 * Half-open: `from` inclusive, `until` exclusive, so consecutive Banking days neither overlap nor
 * leave a gap and an event on the boundary is counted exactly once.
 */
data class BankingDayWindow(
    val date: LocalDate,
    val from: Instant,
    val until: Instant,
)
