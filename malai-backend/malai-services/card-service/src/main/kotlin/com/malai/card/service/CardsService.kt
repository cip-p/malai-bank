package com.malai.card.service

import com.malai.card.model.CardDetailResponse
import com.malai.card.model.CardsResponse
import com.malai.card.model.InternalCardResponse
import com.malai.card.repository.CardRepository
import com.malai.commons.exception.MalaiCode.CARD_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CardsService(private val cardRepository: CardRepository) {

    @Transactional(readOnly = true)
    fun list(accountHolderId: UUID): CardsResponse =
        CardsResponse(
            cardRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId)
                .map { it.toCardResponse() }
        )

    @Transactional(readOnly = true)
    fun detail(cardId: UUID, accountHolderId: UUID): CardDetailResponse =
        (cardRepository.findByCardIdAndAccountHolderId(cardId, accountHolderId)
            ?: throw NotFoundException(CARD_NOT_FOUND))
            .toCardDetailResponse()

    @Transactional(readOnly = true)
    fun resolveForHolder(cardId: UUID, accountHolderId: UUID): InternalCardResponse =
        (cardRepository.findByCardIdAndAccountHolderId(cardId, accountHolderId)
            ?: throw NotFoundException(CARD_NOT_FOUND))
            .toInternalCardResponse()
}
