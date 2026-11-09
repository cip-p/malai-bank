package com.malai.commons.schedule

import com.malai.commons.model.Frequency.ANNUAL
import com.malai.commons.model.Frequency.MONTHLY
import com.malai.commons.model.Frequency.QUARTERLY
import com.malai.commons.model.Frequency.WEEKLY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OccurrenceScheduleTest {

    @Test
    fun weeklyOccurrencesAreSevenDayMultiplesOfTheAnchor() {
        val anchor = LocalDate.parse("2026-06-01")

        assertThat(OccurrenceSchedule.occurrenceOn(anchor, WEEKLY, 0)).isEqualTo(LocalDate.parse("2026-06-01"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, WEEKLY, 1)).isEqualTo(LocalDate.parse("2026-06-08"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, WEEKLY, 3)).isEqualTo(LocalDate.parse("2026-06-22"))
    }

    @Test
    fun monthlyOccurrencesAnchoredOnThe31stClampToMonthEndThenReturnToThe31st() {
        val anchor = LocalDate.parse("2026-01-31")

        assertThat(OccurrenceSchedule.occurrenceOn(anchor, MONTHLY, 0)).isEqualTo(LocalDate.parse("2026-01-31"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, MONTHLY, 1)).isEqualTo(LocalDate.parse("2026-02-28"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, MONTHLY, 2)).isEqualTo(LocalDate.parse("2026-03-31"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, MONTHLY, 3)).isEqualTo(LocalDate.parse("2026-04-30"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, MONTHLY, 4)).isEqualTo(LocalDate.parse("2026-05-31"))
    }

    @Test
    fun monthlyOccurrenceAnchoredOnThe31stClampsToLeapFebruary29() {
        val anchor = LocalDate.parse("2028-01-31")

        assertThat(OccurrenceSchedule.occurrenceOn(anchor, MONTHLY, 1)).isEqualTo(LocalDate.parse("2028-02-29"))
    }

    @Test
    fun quarterlyOccurrencesStepThreeMonthsWithMonthEndClamp() {
        val anchor = LocalDate.parse("2026-01-31")

        assertThat(OccurrenceSchedule.occurrenceOn(anchor, QUARTERLY, 1)).isEqualTo(LocalDate.parse("2026-04-30"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, QUARTERLY, 2)).isEqualTo(LocalDate.parse("2026-07-31"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, QUARTERLY, 3)).isEqualTo(LocalDate.parse("2026-10-31"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, QUARTERLY, 4)).isEqualTo(LocalDate.parse("2027-01-31"))
    }

    @Test
    fun annualOccurrencesAnchoredOnLeapDayClampToFeb28ThenReturnToFeb29() {
        val anchor = LocalDate.parse("2024-02-29")

        assertThat(OccurrenceSchedule.occurrenceOn(anchor, ANNUAL, 1)).isEqualTo(LocalDate.parse("2025-02-28"))
        assertThat(OccurrenceSchedule.occurrenceOn(anchor, ANNUAL, 4)).isEqualTo(LocalDate.parse("2028-02-29"))
    }

    @Test
    fun nextStrictlyAfterReturnsTheAnchorWhenTheDatePrecedesIt() {
        val anchor = LocalDate.parse("2026-06-01")

        assertThat(OccurrenceSchedule.nextStrictlyAfter(anchor, MONTHLY, LocalDate.parse("2026-05-15")))
            .isEqualTo(LocalDate.parse("2026-06-01"))
    }

    @Test
    fun nextStrictlyAfterAdvancesPastAnOccurrenceThatFallsOnTheDate() {
        val anchor = LocalDate.parse("2026-06-01")

        assertThat(OccurrenceSchedule.nextStrictlyAfter(anchor, MONTHLY, LocalDate.parse("2026-06-01")))
            .isEqualTo(LocalDate.parse("2026-07-01"))
    }

    @Test
    fun nextStrictlyAfterResynchronisesForwardToTheFirstFutureOccurrenceWithoutReplayingMissedOnes() {
        val anchor = LocalDate.parse("2026-01-01")

        assertThat(OccurrenceSchedule.nextStrictlyAfter(anchor, MONTHLY, LocalDate.parse("2026-07-20")))
            .isEqualTo(LocalDate.parse("2026-08-01"))
    }

    @Test
    fun nextStrictlyAfterHonoursMonthEndClampWhenResynchronising() {
        val anchor = LocalDate.parse("2026-01-31")

        assertThat(OccurrenceSchedule.nextStrictlyAfter(anchor, MONTHLY, LocalDate.parse("2026-02-15")))
            .isEqualTo(LocalDate.parse("2026-02-28"))
        assertThat(OccurrenceSchedule.nextStrictlyAfter(anchor, MONTHLY, LocalDate.parse("2026-02-28")))
            .isEqualTo(LocalDate.parse("2026-03-31"))
    }

    @Test
    fun nextStrictlyAfterHandlesWeeklyResync() {
        val anchor = LocalDate.parse("2026-06-01")

        assertThat(OccurrenceSchedule.nextStrictlyAfter(anchor, WEEKLY, LocalDate.parse("2026-06-20")))
            .isEqualTo(LocalDate.parse("2026-06-22"))
    }

    @Test
    fun nextStrictlyAfterHandlesQuarterlyAndAnnualResync() {
        assertThat(OccurrenceSchedule.nextStrictlyAfter(LocalDate.parse("2026-01-15"), QUARTERLY, LocalDate.parse("2026-08-01")))
            .isEqualTo(LocalDate.parse("2026-10-15"))
        assertThat(OccurrenceSchedule.nextStrictlyAfter(LocalDate.parse("2020-03-10"), ANNUAL, LocalDate.parse("2026-07-07")))
            .isEqualTo(LocalDate.parse("2027-03-10"))
    }
}
