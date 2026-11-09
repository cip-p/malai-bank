package com.malai.card.model

import java.time.Instant
import java.util.UUID

enum class CardAuthorisationStatus { AUTHORISED, DECLINED, SETTLED, REVERSED }

enum class AuthorisationDecision { APPROVED, DECLINED }

enum class DeclineReason { CARD_FROZEN, CARD_CANCELLED, INSUFFICIENT_FUNDS, OVER_LIMIT, UNKNOWN_CARD }

data class AuthoriseCardRequest(
    val schemeReference: UUID,
    val pan: String,
    val amountMinorUnits: Long,
    val currency: String,
    val merchantName: String,
    val mcc: String,
)

data class AuthoriseCardResponse(
    val authorisationId: UUID,
    val decision: AuthorisationDecision,
    val declineReason: DeclineReason? = null,
)

data class AccountAuthorisationsResponse(
    val authorisations: List<AccountAuthorisationResponse>,
)

data class AccountAuthorisationResponse(
    val authorisationId: UUID,
    val cardId: UUID?,
    val status: CardAuthorisationStatus,
    val merchantName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val declineReason: DeclineReason?,
    val createdAt: Instant,
)

data class InternalAuthorisationResponse(
    val authorisationId: UUID,
    val schemeReference: UUID,
    val status: CardAuthorisationStatus,
    val amountMinorUnits: Long,
    val currency: String,
)
