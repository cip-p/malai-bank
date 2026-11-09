package com.malai.card.model

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class IssueCardRequest(
    @field:NotNull val accountId: UUID?,
    @field:NotNull val accountHolderId: UUID?,
    @field:NotNull val cardholderName: String?,
    @field:NotNull val variant: CardVariant?,
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

data class CardReplacementResponse(
    val cancelledCardId: UUID,
    val replacementCard: CardResponse,
)

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

data class InternalCardResponse(
    val cardId: UUID,
    val accountId: UUID,
    val pan: String,
)
