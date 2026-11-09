package com.malai.client.malai.card.model

import java.util.UUID

enum class CardVariant { DEBIT, CREDIT }

enum class CardStatus { ACTIVE, FROZEN, CANCELLED }

data class IssueCardRequest(
    val accountId: UUID,
    val accountHolderId: UUID,
    val cardholderName: String,
    val variant: CardVariant,
)

data class IssueCardResponse(
    val cardId: UUID,
    val accountId: UUID,
    val maskedPan: String,
    val variant: CardVariant,
    val status: CardStatus,
    val expiry: String,
)

data class CardResponse(
    val cardId: UUID,
    val accountId: UUID,
    val maskedPan: String,
    val variant: CardVariant,
    val status: CardStatus,
    val expiry: String,
)

data class CardsResponse(val cards: List<CardResponse>)

data class CardDetailResponse(
    val cardId: UUID,
    val accountId: UUID,
    val cardholderName: String,
    val pan: String,
    val expiry: String,
    val cvv: String,
    val variant: CardVariant,
    val status: CardStatus,
)

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

enum class AuthorisationStatus { AUTHORISED, DECLINED }

data class AccountAuthorisationsResponse(val authorisations: List<AccountAuthorisationResponse>)

data class AccountAuthorisationResponse(
    val authorisationId: UUID,
    val cardId: UUID?,
    val status: AuthorisationStatus,
    val merchantName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val declineReason: DeclineReason? = null,
    val createdAt: String,
)

data class StatementFiguresResponse(
    val statementBalanceMinorUnits: Long,
    val minimumPaymentMinorUnits: Long,
    val dueDate: String,
)

data class InternalCardResponse(
    val cardId: UUID,
    val accountId: UUID,
    val pan: String,
)

enum class CardAuthorisationStatus { AUTHORISED, DECLINED, SETTLED, REVERSED }

data class InternalAuthorisationResponse(
    val authorisationId: UUID,
    val schemeReference: UUID,
    val status: CardAuthorisationStatus,
    val amountMinorUnits: Long,
    val currency: String,
)
