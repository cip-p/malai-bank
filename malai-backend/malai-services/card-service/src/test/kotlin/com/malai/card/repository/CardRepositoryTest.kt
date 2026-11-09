package com.malai.card.repository

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardStatus.CANCELLED
import com.malai.card.model.CardVariant.CREDIT
import com.malai.card.model.CardVariant.DEBIT
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CardRepositoryTest : CardAcceptanceTest() {

    private val windowFrom: Instant = Instant.parse("2026-07-19T23:00:00Z")
    private val windowUntil: Instant = Instant.parse("2026-07-20T23:00:00Z")

    private val firstAccountId = UUID.fromString("00000000-0000-4000-6000-0000000000a1")
    private val secondAccountId = UUID.fromString("00000000-0000-4000-6000-0000000000a2")
    private val thirdAccountId = UUID.fromString("00000000-0000-4000-6000-0000000000a3")
    private val firstCardId = UUID.fromString("00000000-0000-4000-6000-0000000000b1")
    private val secondCardId = UUID.fromString("00000000-0000-4000-6000-0000000000b2")
    private val thirdCardId = UUID.fromString("00000000-0000-4000-6000-0000000000b3")

    private fun cardsIssued(): Long =
        cardRepository().countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(windowFrom, windowUntil)

    @Test
    fun countsEveryCardIssuedInTheWindowRegardlessOfVariantOrLaterCancellation() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(cardId = firstCardId, accountId = firstAccountId, accountHolderId = someAccountHolderUid(),
            variant = DEBIT, status = ACTIVE, createdAt = Instant.parse("2026-07-20T09:00:00Z")))
        and(aCardExists(cardId = secondCardId, accountId = secondAccountId, accountHolderId = someAccountHolderUid(),
            variant = CREDIT, status = ACTIVE, createdAt = Instant.parse("2026-07-20T10:00:00Z")))
        and(aCardExists(cardId = thirdCardId, accountId = thirdAccountId, accountHolderId = someAccountHolderUid(),
            variant = DEBIT, status = CANCELLED, createdAt = Instant.parse("2026-07-20T11:00:00Z")))

        cardsIssued() shouldBe 3L
    }

    @Test
    fun isHalfOpenSoTheLowerBoundCountsAndTheUpperBoundDoesNot() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(cardId = firstCardId, accountId = firstAccountId, accountHolderId = someAccountHolderUid(),
            createdAt = windowFrom))
        and(aCardExists(cardId = secondCardId, accountId = secondAccountId, accountHolderId = someAccountHolderUid(),
            createdAt = windowUntil))

        cardsIssued() shouldBe 1L
    }

    @Test
    fun excludesCardsIssuedBeforeTheWindow() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(cardId = firstCardId, accountId = firstAccountId, accountHolderId = someAccountHolderUid(),
            createdAt = Instant.parse("2026-07-19T22:59:59Z")))

        cardsIssued() shouldBe 0L
    }

    @Test
    fun isZeroWhenNoCardsExist() = malaiAcceptanceTest {
        given(thereAreNoCards())

        cardsIssued() shouldBe 0L
    }
}
