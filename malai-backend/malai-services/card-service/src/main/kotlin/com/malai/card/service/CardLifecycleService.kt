package com.malai.card.service

import com.malai.card.model.CardReplacementResponse
import com.malai.card.model.CardResponse
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardStatus.CANCELLED
import com.malai.card.model.CardStatus.FROZEN
import com.malai.card.model.IssueCardRequest
import com.malai.card.repository.CardRepository
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.CARD_INVALID_STATE_TRANSITION
import com.malai.commons.exception.MalaiCode.CARD_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import com.malai.security.MalaiUserWithAccountHolderId
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CardLifecycleService(
    private val cardRepository: CardRepository,
    private val cardIssuer: CardIssuer,
    private val auditWriter: AuditWriter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun freeze(user: MalaiUserWithAccountHolderId, cardId: UUID): CardResponse {
        val card = ownedCard(cardId, user.accountHolderId)
        if (card.status != ACTIVE) throw ConflictErrorException(CARD_INVALID_STATE_TRANSITION)

        val frozen = cardRepository.save(card.copy(status = FROZEN))

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "CARD_FROZEN",
                resourceType = "Card",
                resourceId = cardId.toString(),
                beforeState = mapOf("status" to card.status.name),
                afterState = mapOf("status" to frozen.status.name),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )

        log.info("card frozen: cardId={}", cardId)
        return frozen.toCardResponse()
    }

    @Transactional
    fun unfreeze(user: MalaiUserWithAccountHolderId, cardId: UUID): CardResponse {
        val card = ownedCard(cardId, user.accountHolderId)
        if (card.status != FROZEN) throw ConflictErrorException(CARD_INVALID_STATE_TRANSITION)

        val reactivated = cardRepository.save(card.copy(status = ACTIVE))

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "CARD_UNFROZEN",
                resourceType = "Card",
                resourceId = cardId.toString(),
                beforeState = mapOf("status" to card.status.name),
                afterState = mapOf("status" to reactivated.status.name),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )

        log.info("card unfrozen: cardId={}", cardId)
        return reactivated.toCardResponse()
    }

    @Transactional
    fun reportLostOrStolen(user: MalaiUserWithAccountHolderId, cardId: UUID): CardReplacementResponse {
        val card = ownedCard(cardId, user.accountHolderId)
        if (card.status == CANCELLED) throw ConflictErrorException(CARD_INVALID_STATE_TRANSITION)

        cardRepository.saveAndFlush(card.copy(status = CANCELLED))

        val replacement = cardIssuer.issue(
            IssueCardRequest(
                accountId = card.accountId,
                accountHolderId = card.accountHolderId,
                cardholderName = card.cardholderName,
                variant = card.variant,
            ),
        )

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "CARD_REPORTED_LOST_OR_STOLEN",
                resourceType = "Card",
                resourceId = cardId.toString(),
                beforeState = mapOf("status" to card.status.name),
                afterState = mapOf(
                    "status" to CANCELLED.name,
                    "replacementCardId" to replacement.cardId.toString(),
                    "replacementPan" to replacement.pan,
                ),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )

        log.info("card reported lost/stolen: cardId={} replacementCardId={}", cardId, replacement.cardId)
        return CardReplacementResponse(
            cancelledCardId = cardId,
            replacementCard = replacement.toCardResponse(),
        )
    }

    private fun ownedCard(cardId: UUID, accountHolderId: UUID) =
        cardRepository.findByCardIdAndAccountHolderId(cardId, accountHolderId)
            ?: throw NotFoundException(CARD_NOT_FOUND)

    companion object {
        private const val MDC_TRACE_ID = "trace_id"
    }
}
