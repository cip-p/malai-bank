package com.malai.card.service

import com.malai.card.model.AccountAuthorisationsResponse
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.DECLINED
import com.malai.card.model.InternalAuthorisationResponse
import com.malai.card.repository.CardAuthorisationRepository
import com.malai.commons.exception.MalaiCode.CARD_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccountAuthorisationsService(private val cardAuthorisationRepository: CardAuthorisationRepository) {

    @Transactional(readOnly = true)
    fun listForAccount(accountId: UUID): AccountAuthorisationsResponse =
        AccountAuthorisationsResponse(
            cardAuthorisationRepository
                .findByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, FEED_STATUSES)
                .map { it.toAccountAuthorisationResponse() }
        )

    @Transactional(readOnly = true)
    fun resolveForHolder(authorisationId: UUID, accountHolderId: UUID): InternalAuthorisationResponse =
        (cardAuthorisationRepository.findByAuthorisationIdAndAccountHolderId(authorisationId, accountHolderId)
            ?: throw NotFoundException(CARD_NOT_FOUND))
            .toInternalAuthorisationResponse()

    private companion object {
        private val FEED_STATUSES = listOf(AUTHORISED, DECLINED)
    }
}
