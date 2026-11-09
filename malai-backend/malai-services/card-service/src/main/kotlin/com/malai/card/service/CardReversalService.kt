package com.malai.card.service

import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.messages.CardReversalNotify
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.REVERSED
import com.malai.card.repository.CardAuthorisationRepository
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.model.ReleaseHoldRequest
import com.malai.commons.TimeService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CardReversalService(
    private val cardAuthorisationRepository: CardAuthorisationRepository,
    private val ledgerClient: LedgerClient,
    private val cardServiceTokenClient: CardServiceTokenClient,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun reverse(notify: CardReversalNotify) {
        val authorisation = cardAuthorisationRepository.findBySchemeReference(notify.schemeReference)
        if (authorisation == null) {
            log.info("reversal ignored, unknown scheme reference: schemeReference={}", notify.schemeReference)
            return
        }
        if (authorisation.status != AUTHORISED) {
            log.info(
                "reversal ignored, authorisation not AUTHORISED: authorisationId={} status={}",
                authorisation.authorisationId, authorisation.status,
            )
            return
        }

        releaseHold(authorisation)
        cardAuthorisationRepository.save(authorisation.copy(status = REVERSED))

        log.info("reversal applied: authorisationId={}", authorisation.authorisationId)
    }

    private fun releaseHold(authorisation: CardAuthorisationEntity) {
        val holdId = requireNotNull(authorisation.holdId) {
            "AUTHORISED authorisation ${authorisation.authorisationId} has no hold_id; cannot reverse"
        }
        ledgerClient.releaseHold(
            cardServiceTokenClient.getToken(),
            holdId,
            ReleaseHoldRequest(resolvedAt = timeService.now().toString()),
        )
    }
}
