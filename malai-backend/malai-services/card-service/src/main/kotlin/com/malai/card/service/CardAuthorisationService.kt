package com.malai.card.service

import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.model.AuthoriseCardRequest
import com.malai.card.model.AuthoriseCardResponse
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.DECLINED
import com.malai.card.repository.CardAuthorisationRepository
import com.malai.card.repository.CardRepository
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.LedgerInsufficientFundsException
import com.malai.client.malai.ledger.model.PlaceHoldRequest
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.Currency
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CardAuthorisationService(
    private val cardRepository: CardRepository,
    private val cardAuthorisationRepository: CardAuthorisationRepository,
    private val decisioner: AuthorisationDecisioner,
    private val ledgerClient: LedgerClient,
    private val cardServiceTokenClient: CardServiceTokenClient,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
) {

    @Transactional
    fun authorise(request: AuthoriseCardRequest): AuthoriseCardResponse {
        val card = cardRepository.findByPan(request.pan)
        val authorisationId = uuidGenerator.generate()
        val now = timeService.now()

        val holdOutcome =
            if (decisioner.requiresHold(card)) placeHold(authorisationId, card!!.accountId, request.amountMinorUnits, now)
            else HoldOutcome.NOT_ATTEMPTED

        val decision = decisioner.decide(card, holdOutcome)
        val approved = decision is AuthorisationDecision.Approved

        val entity = CardAuthorisationEntity(
            authorisationId = authorisationId,
            cardId = card?.cardId,
            accountId = card?.accountId,
            accountHolderId = card?.accountHolderId,
            schemeReference = request.schemeReference,
            merchantName = request.merchantName,
            mcc = request.mcc,
            amountMinorUnits = request.amountMinorUnits,
            currency = Currency.valueOf(request.currency),
            status = if (approved) AUTHORISED else DECLINED,
            declineReason = (decision as? AuthorisationDecision.Declined)?.reason,
            holdId = if (approved) authorisationId else null,
            createdAt = now,
        )

        return cardAuthorisationRepository.save(entity).toAuthoriseResponse()
    }

    private fun placeHold(authorisationId: UUID, accountId: UUID, amountMinorUnits: Long, now: Instant): HoldOutcome =
        try {
            ledgerClient.placeHold(
                cardServiceTokenClient.getToken(),
                PlaceHoldRequest(
                    holdId = authorisationId,
                    accountId = accountId,
                    amountMinorUnits = amountMinorUnits,
                    placedAt = now.toString(),
                ),
            )
            HoldOutcome.PLACED
        } catch (ex: LedgerInsufficientFundsException) {
            HoldOutcome.REJECTED_INSUFFICIENT_FUNDS
        }
}
