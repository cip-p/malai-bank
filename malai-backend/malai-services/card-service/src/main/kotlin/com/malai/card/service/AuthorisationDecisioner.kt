package com.malai.card.service

import com.malai.card.entity.CardEntity
import com.malai.card.model.CardStatus
import com.malai.card.model.CardVariant
import com.malai.card.model.DeclineReason
import org.springframework.stereotype.Component

enum class HoldOutcome { PLACED, REJECTED_INSUFFICIENT_FUNDS, NOT_ATTEMPTED }

sealed interface AuthorisationDecision {
    data object Approved : AuthorisationDecision
    data class Declined(val reason: DeclineReason) : AuthorisationDecision
}

@Component
class AuthorisationDecisioner {

    fun requiresHold(card: CardEntity?): Boolean =
        card != null && card.status == CardStatus.ACTIVE

    fun decide(card: CardEntity?, holdOutcome: HoldOutcome): AuthorisationDecision = when {
        card == null -> AuthorisationDecision.Declined(DeclineReason.UNKNOWN_CARD)
        card.status == CardStatus.FROZEN -> AuthorisationDecision.Declined(DeclineReason.CARD_FROZEN)
        card.status == CardStatus.CANCELLED -> AuthorisationDecision.Declined(DeclineReason.CARD_CANCELLED)
        holdOutcome == HoldOutcome.REJECTED_INSUFFICIENT_FUNDS ->
            AuthorisationDecision.Declined(fundsReasonFor(card.variant))
        else -> AuthorisationDecision.Approved
    }

    private fun fundsReasonFor(variant: CardVariant): DeclineReason = when (variant) {
        CardVariant.CREDIT -> DeclineReason.OVER_LIMIT
        CardVariant.DEBIT -> DeclineReason.INSUFFICIENT_FUNDS
    }
}
