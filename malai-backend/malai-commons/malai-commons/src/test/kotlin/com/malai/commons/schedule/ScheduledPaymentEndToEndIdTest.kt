package com.malai.commons.schedule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ScheduledPaymentEndToEndIdTest {

    private val instrument = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val otherInstrument = UUID.fromString("00000000-0000-4000-8000-000000000002")

    @Test
    fun theSameInstrumentAndOccurrenceDateAlwaysDeriveTheSameId() {
        val first = ScheduledPaymentEndToEndId.forOccurrence(instrument, LocalDate.parse("2026-07-07"))
        val second = ScheduledPaymentEndToEndId.forOccurrence(instrument, LocalDate.parse("2026-07-07"))

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun differentOccurrenceDatesOfTheSameInstrumentDeriveDifferentIds() {
        val july = ScheduledPaymentEndToEndId.forOccurrence(instrument, LocalDate.parse("2026-07-07"))
        val august = ScheduledPaymentEndToEndId.forOccurrence(instrument, LocalDate.parse("2026-08-07"))

        assertThat(july).isNotEqualTo(august)
    }

    @Test
    fun differentInstrumentsOnTheSameDateDeriveDifferentIds() {
        val date = LocalDate.parse("2026-07-07")

        assertThat(ScheduledPaymentEndToEndId.forOccurrence(instrument, date))
            .isNotEqualTo(ScheduledPaymentEndToEndId.forOccurrence(otherInstrument, date))
    }
}
