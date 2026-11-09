package com.malai.card.service

import com.malai.card.model.CardsIssuedResponse
import com.malai.card.repository.CardRepository
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Counts Cards issued in a window, for whichever caller is composing a day-scoped figure. The window
 * arrives as two instants rather than a date because the *meaning* of a day belongs to the caller,
 * not here — this context knows only when its rows were written.
 *
 * The count is of issuance events, not surviving cards: it filters on `created_at` alone and never
 * on status, so a card issued and then cancelled the same day still counts and no bank's "cards
 * issued today" silently decreases. Both variants count, and a replacement issued after a loss is
 * an issuance like any other.
 */
@Service
class InternalCardMetricsService(
    private val cardRepository: CardRepository,
) {

    fun cardsIssued(from: Instant, until: Instant) = CardsIssuedResponse(
        cardRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, until)
    )
}
