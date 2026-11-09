package com.malai.commons.schedule

import com.malai.commons.model.Frequency
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Anchor-based occurrence arithmetic shared by the standing-order executor and the
 * direct-debit collection requester (issue 08 reuses this same component).
 *
 * Every occurrence is the immutable start-date anchor plus n periods of the frequency.
 * Month and year steps clamp to month-end in shorter months and return to the anchor's
 * day-of-month once a long-enough month comes round again — this falls straight out of
 * java.time's plusMonths / plusYears, which compute from the anchor rather than iterating,
 * so a 31st-anchored monthly order pays the 28th of February and the 31st of March.
 *
 * Occurrences are strictly increasing in n, which is what lets [nextStrictlyAfter]
 * resynchronise a schedule forward after downtime without ever replaying missed dates.
 */
object OccurrenceSchedule {

    fun occurrenceOn(anchor: LocalDate, frequency: Frequency, n: Int): LocalDate = when (frequency) {
        Frequency.WEEKLY -> anchor.plusWeeks(n.toLong())
        Frequency.MONTHLY -> anchor.plusMonths(n.toLong())
        Frequency.QUARTERLY -> anchor.plusMonths(3L * n)
        Frequency.ANNUAL -> anchor.plusYears(n.toLong())
    }

    fun nextStrictlyAfter(anchor: LocalDate, frequency: Frequency, date: LocalDate): LocalDate {
        var n = floorEstimate(anchor, frequency, date)
        while (n > 0 && occurrenceOn(anchor, frequency, n - 1).isAfter(date)) n--
        while (!occurrenceOn(anchor, frequency, n).isAfter(date)) n++
        return occurrenceOn(anchor, frequency, n)
    }

    private fun floorEstimate(anchor: LocalDate, frequency: Frequency, date: LocalDate): Int {
        if (!date.isAfter(anchor)) return 0
        val periods = when (frequency) {
            Frequency.WEEKLY -> ChronoUnit.WEEKS.between(anchor, date)
            Frequency.MONTHLY -> ChronoUnit.MONTHS.between(anchor, date)
            Frequency.QUARTERLY -> ChronoUnit.MONTHS.between(anchor, date) / 3
            Frequency.ANNUAL -> ChronoUnit.YEARS.between(anchor, date)
        }
        return periods.coerceAtLeast(0L).toInt()
    }
}
