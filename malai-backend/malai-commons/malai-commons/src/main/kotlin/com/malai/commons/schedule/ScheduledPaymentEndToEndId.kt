package com.malai.commons.schedule

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.util.UUID

/**
 * Derives the deterministic end-to-end id a scheduler stamps on each occurrence's payment.
 *
 * A given (instrument, occurrence date) pair always maps to the same id, so a re-fired
 * occurrence — a scheduler restart between initiate and advance — bounces harmlessly off
 * the payments service's unique end-to-end-id key rather than paying twice (ADR 0004).
 * Shared by the standing-order executor and the direct-debit collection requester.
 */
object ScheduledPaymentEndToEndId {

    fun forOccurrence(instrumentId: UUID, occurrenceDate: LocalDate): UUID =
        UUID.nameUUIDFromBytes("$instrumentId:$occurrenceDate".toByteArray(UTF_8))
}
