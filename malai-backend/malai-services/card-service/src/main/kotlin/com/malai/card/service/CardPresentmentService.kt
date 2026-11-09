package com.malai.card.service

import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.card.repository.CardAuthorisationRepository
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.model.LedgerEntryRequest
import com.malai.client.malai.ledger.model.PostTransactionRequest
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CardPresentmentService(
    private val cardAuthorisationRepository: CardAuthorisationRepository,
    private val ledgerClient: LedgerClient,
    private val cardServiceTokenClient: CardServiceTokenClient,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun present(notify: CardPresentmentNotify) {
        val authorisation = cardAuthorisationRepository.findBySchemeReference(notify.schemeReference)
        if (authorisation == null) {
            log.info("presentment ignored, unknown scheme reference: schemeReference={}", notify.schemeReference)
            return
        }
        if (authorisation.status == SETTLED) {
            log.info("presentment ignored, authorisation already settled: authorisationId={}", authorisation.authorisationId)
            return
        }
        if (authorisation.status != AUTHORISED) {
            log.info(
                "presentment ignored, authorisation not AUTHORISED: authorisationId={} status={}",
                authorisation.authorisationId, authorisation.status,
            )
            return
        }
        if (notify.presentedAmountMinorUnits > authorisation.amountMinorUnits) {
            log.info(
                "presentment refused, presented amount exceeds authorised: authorisationId={} presented={} authorised={}",
                authorisation.authorisationId, notify.presentedAmountMinorUnits, authorisation.amountMinorUnits,
            )
            return
        }

        settleHold(authorisation, notify, timeService.now())
        cardAuthorisationRepository.save(authorisation.copy(status = SETTLED))

        log.info(
            "presentment settled: authorisationId={} presented={} merchant={}",
            authorisation.authorisationId, notify.presentedAmountMinorUnits, notify.merchantName,
        )
    }

    private fun settleHold(authorisation: CardAuthorisationEntity, notify: CardPresentmentNotify, now: Instant) {
        val holdId = requireNotNull(authorisation.holdId) {
            "AUTHORISED authorisation ${authorisation.authorisationId} has no hold_id; cannot settle"
        }
        val accountId = requireNotNull(authorisation.accountId) {
            "AUTHORISED authorisation ${authorisation.authorisationId} has no account_id; cannot settle"
        }
        ledgerClient.settleHold(
            cardServiceTokenClient.getToken(),
            holdId,
            PostTransactionRequest(
                transactionId = uuidGenerator.generate(),
                type = "CARD_PRESENTMENT",
                initiatedById = authorisation.accountHolderId ?: authorisation.authorisationId,
                postedAt = now.toString(),
                description = notify.merchantName,
                entries = listOf(
                    LedgerEntryRequest(
                        accountId = accountId,
                        amountMinorUnits = -notify.presentedAmountMinorUnits,
                        counterpartyId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                        description = notify.merchantName,
                        merchantName = notify.merchantName,
                    ),
                    LedgerEntryRequest(
                        accountId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                        amountMinorUnits = notify.presentedAmountMinorUnits,
                        counterpartyId = accountId,
                        description = notify.merchantName,
                    ),
                ),
            ),
            settleAmountMinorUnits = notify.presentedAmountMinorUnits,
        )
    }

    companion object {
        val SCHEME_SETTLEMENT_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8001-000000010002")
    }
}
