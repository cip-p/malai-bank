package com.malai.card.service

import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.entity.CardEntity
import com.malai.card.model.AccountAuthorisationResponse
import com.malai.card.model.AuthorisationDecision.APPROVED
import com.malai.card.model.AuthorisationDecision.DECLINED
import com.malai.card.model.AuthoriseCardResponse
import com.malai.card.model.CardAuthorisationStatus
import com.malai.card.model.CardDetailResponse
import com.malai.card.model.CardResponse
import com.malai.card.model.InternalAuthorisationResponse
import com.malai.card.model.InternalCardResponse
import com.malai.card.model.IssueCardResponse
import com.malai.card.pan.Pan

fun CardEntity.toCardResponse() = CardResponse(
    cardId = cardId,
    accountId = accountId,
    maskedPan = Pan.mask(pan),
    variant = variant,
    status = status,
    expiry = expiry,
)

fun CardEntity.toCardDetailResponse() = CardDetailResponse(
    cardId = cardId,
    accountId = accountId,
    cardholderName = cardholderName,
    pan = pan,
    expiry = expiry,
    cvv = cvv,
    variant = variant,
    status = status,
)

fun CardEntity.toInternalCardResponse() = InternalCardResponse(
    cardId = cardId,
    accountId = accountId,
    pan = pan,
)

fun CardEntity.toIssueResponse() = IssueCardResponse(
    cardId = cardId,
    accountId = accountId,
    maskedPan = Pan.mask(pan),
    variant = variant,
    status = status,
    expiry = expiry,
)

fun CardAuthorisationEntity.toAuthoriseResponse() = AuthoriseCardResponse(
    authorisationId = authorisationId,
    decision = if (status == CardAuthorisationStatus.AUTHORISED) APPROVED else DECLINED,
    declineReason = declineReason,
)

fun CardAuthorisationEntity.toAccountAuthorisationResponse() = AccountAuthorisationResponse(
    authorisationId = authorisationId,
    cardId = cardId,
    status = status,
    merchantName = merchantName,
    amountMinorUnits = amountMinorUnits,
    currency = currency.name,
    declineReason = declineReason,
    createdAt = createdAt,
)

fun CardAuthorisationEntity.toInternalAuthorisationResponse() = InternalAuthorisationResponse(
    authorisationId = authorisationId,
    schemeReference = schemeReference,
    status = status,
    amountMinorUnits = amountMinorUnits,
    currency = currency.name,
)
