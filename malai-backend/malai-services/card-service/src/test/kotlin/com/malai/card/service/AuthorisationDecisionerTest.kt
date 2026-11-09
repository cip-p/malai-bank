package com.malai.card.service

import com.malai.card.entity.CardEntity
import com.malai.card.model.CardStatus
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardStatus.CANCELLED
import com.malai.card.model.CardStatus.FROZEN
import com.malai.card.model.CardVariant
import com.malai.card.model.CardVariant.CREDIT
import com.malai.card.model.CardVariant.DEBIT
import com.malai.card.model.DeclineReason.CARD_CANCELLED
import com.malai.card.model.DeclineReason.CARD_FROZEN
import com.malai.card.model.DeclineReason.INSUFFICIENT_FUNDS
import com.malai.card.model.DeclineReason.OVER_LIMIT
import com.malai.card.model.DeclineReason.UNKNOWN_CARD
import com.malai.card.service.AuthorisationDecision.Approved
import com.malai.card.service.AuthorisationDecision.Declined
import com.malai.card.service.HoldOutcome.NOT_ATTEMPTED
import com.malai.card.service.HoldOutcome.PLACED
import com.malai.card.service.HoldOutcome.REJECTED_INSUFFICIENT_FUNDS
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AuthorisationDecisionerTest {

    private val decisioner = AuthorisationDecisioner()

    @Test
    fun `an unknown card is declined UNKNOWN_CARD with no hold attempted`() {
        decisioner.requiresHold(null) shouldBe false
        decisioner.decide(null, NOT_ATTEMPTED) shouldBe Declined(UNKNOWN_CARD)
    }

    @Test
    fun `a frozen card is declined CARD_FROZEN with no hold attempted`() {
        val card = aCard(status = FROZEN)
        decisioner.requiresHold(card) shouldBe false
        decisioner.decide(card, NOT_ATTEMPTED) shouldBe Declined(CARD_FROZEN)
    }

    @Test
    fun `a cancelled card is declined CARD_CANCELLED with no hold attempted`() {
        val card = aCard(status = CANCELLED)
        decisioner.requiresHold(card) shouldBe false
        decisioner.decide(card, NOT_ATTEMPTED) shouldBe Declined(CARD_CANCELLED)
    }

    @Test
    fun `an active card requires a hold`() {
        decisioner.requiresHold(aCard(status = ACTIVE)) shouldBe true
    }

    @Test
    fun `an active card whose hold is placed is approved`() {
        decisioner.decide(aCard(status = ACTIVE), PLACED) shouldBe Approved
    }

    @Test
    fun `an active debit card whose hold is rejected is declined INSUFFICIENT_FUNDS`() {
        decisioner.decide(aCard(status = ACTIVE, variant = DEBIT), REJECTED_INSUFFICIENT_FUNDS) shouldBe
            Declined(INSUFFICIENT_FUNDS)
    }

    @Test
    fun `an active credit card whose hold is rejected is declined OVER_LIMIT`() {
        decisioner.decide(aCard(status = ACTIVE, variant = CREDIT), REJECTED_INSUFFICIENT_FUNDS) shouldBe
            Declined(OVER_LIMIT)
    }

    private fun aCard(status: CardStatus, variant: CardVariant = DEBIT) = CardEntity(
        cardId = UUID.randomUUID(),
        accountId = UUID.randomUUID(),
        accountHolderId = UUID.randomUUID(),
        cardholderName = "Some Cardholder",
        pan = "9904320000012341",
        expiry = "10/29",
        cvv = "123",
        variant = variant,
        status = status,
        createdAt = Instant.parse("2026-10-15T10:30:00Z"),
    )
}
