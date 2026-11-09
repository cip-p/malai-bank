package com.malai.commons.time

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BankingDayTest {

    @Test
    fun duringBritishSummerTimeTheDayStartsAnHourBeforeUtcMidnight() {
        val window = BankingDay.containing(Instant.parse("2026-07-20T14:03:11Z"))

        assertThat(window.date).isEqualTo(LocalDate.parse("2026-07-20"))
        assertThat(window.from).isEqualTo(Instant.parse("2026-07-19T23:00:00Z"))
        assertThat(window.until).isEqualTo(Instant.parse("2026-07-20T23:00:00Z"))
    }

    @Test
    fun duringGreenwichMeanTimeTheDayStartsAtUtcMidnight() {
        val window = BankingDay.containing(Instant.parse("2026-01-15T14:03:11Z"))

        assertThat(window.date).isEqualTo(LocalDate.parse("2026-01-15"))
        assertThat(window.from).isEqualTo(Instant.parse("2026-01-15T00:00:00Z"))
        assertThat(window.until).isEqualTo(Instant.parse("2026-01-16T00:00:00Z"))
    }

    @Test
    fun anInstantJustBeforeLondonMidnightBelongsToTheDayThatIsEnding() {
        val window = BankingDay.containing(Instant.parse("2026-07-19T22:59:59Z"))

        assertThat(window.date).isEqualTo(LocalDate.parse("2026-07-19"))
    }

    @Test
    fun anInstantAtLondonMidnightBelongsToTheDayThatIsBeginning() {
        val window = BankingDay.containing(Instant.parse("2026-07-19T23:00:00Z"))

        assertThat(window.date).isEqualTo(LocalDate.parse("2026-07-20"))
    }

    @Test
    fun aUtcMidnightInSummerStillBelongsToTheLondonDayAlreadyUnderway() {
        val window = BankingDay.containing(Instant.parse("2026-07-20T00:00:00Z"))

        assertThat(window.date).isEqualTo(LocalDate.parse("2026-07-20"))
        assertThat(window.from).isEqualTo(Instant.parse("2026-07-19T23:00:00Z"))
    }

    @Test
    fun theWindowIsHalfOpenSoConsecutiveDaysNeitherOverlapNorLeaveAGap() {
        val ending = BankingDay.containing(Instant.parse("2026-03-28T12:00:00Z"))
        val beginning = BankingDay.containing(Instant.parse("2026-03-29T12:00:00Z"))

        assertThat(ending.until).isEqualTo(beginning.from)
    }

    @Test
    fun theSpringForwardDayIsAnHourShort() {
        val window = BankingDay.containing(Instant.parse("2026-03-29T12:00:00Z"))

        assertThat(window.date).isEqualTo(LocalDate.parse("2026-03-29"))
        assertThat(window.from).isEqualTo(Instant.parse("2026-03-29T00:00:00Z"))
        assertThat(window.until).isEqualTo(Instant.parse("2026-03-29T23:00:00Z"))
    }
}
