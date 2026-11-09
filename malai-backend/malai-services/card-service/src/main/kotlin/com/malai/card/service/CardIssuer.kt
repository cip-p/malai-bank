package com.malai.card.service

import com.malai.card.entity.CardEntity
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.IssueCardRequest
import com.malai.card.pan.Pan
import com.malai.card.pan.PanGenerator
import com.malai.card.repository.CardRepository
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CardIssuer(
    private val cardRepository: CardRepository,
    private val panGenerator: PanGenerator,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
) {

    @Transactional
    fun issue(request: IssueCardRequest): CardEntity {
        cardRepository.findByAccountIdAndStatus(request.accountId!!, ACTIVE)?.let { return it }

        val issuedAt = timeService.now()
        return cardRepository.save(
            CardEntity(
                cardId = uuidGenerator.generate(),
                accountId = request.accountId,
                accountHolderId = request.accountHolderId!!,
                cardholderName = request.cardholderName!!,
                pan = panGenerator.generatePan(),
                expiry = Pan.expiryFor(issuedAt),
                cvv = panGenerator.generateCvv(),
                variant = request.variant!!,
                status = ACTIVE,
                createdAt = issuedAt,
            )
        )
    }
}
