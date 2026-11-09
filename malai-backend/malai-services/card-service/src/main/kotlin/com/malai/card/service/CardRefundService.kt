package com.malai.card.service

import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.entity.CardRefundEntity
import com.malai.card.messages.CardRefundNotify
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.card.repository.CardAuthorisationRepository
import com.malai.card.repository.CardRefundRepository
import com.malai.card.service.CardPresentmentService.Companion.SCHEME_SETTLEMENT_ACCOUNT_ID
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.model.LedgerEntryRequest
import com.malai.client.malai.ledger.model.PostTransactionRequest
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CardRefundService(
    private val cardAuthorisationRepository: CardAuthorisationRepository,
    private val cardRefundRepository: CardRefundRepository,
    private val ledgerClient: LedgerClient,
    private val cardServiceTokenClient: CardServiceTokenClient,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun refund(notify: CardRefundNotify) {
        val authorisation = cardAuthorisationRepository.findBySchemeReference(notify.schemeReference)
        if (authorisation == null) {
            log.info("refund ignored, unknown scheme reference: schemeReference={}", notify.schemeReference)
            return
        }
        if (authorisation.status != SETTLED) {
            log.info(
                "refund ignored, referenced purchase not SETTLED: authorisationId={} status={}",
                authorisation.authorisationId, authorisation.status,
            )
            return
        }
        if (cardRefundRepository.existsByRefundReference(notify.refundReference)) {
            log.info("refund ignored, already recorded: refundReference={}", notify.refundReference)
            return
        }

        val transactionId = uuidGenerator.generate()
        creditRefund(authorisation, notify, transactionId)
        cardRefundRepository.save(
            CardRefundEntity(
                refundReference = notify.refundReference,
                authorisationId = authorisation.authorisationId,
                accountId = requireNotNull(authorisation.accountId),
                schemeReference = authorisation.schemeReference,
                merchantName = authorisation.merchantName,
                amountMinorUnits = notify.refundAmountMinorUnits,
                currency = authorisation.currency,
                transactionId = transactionId,
                createdAt = timeService.now(),
            )
        )

        log.info(
            "refund recorded: refundReference={} authorisationId={} amount={} merchant={}",
            notify.refundReference, authorisation.authorisationId, notify.refundAmountMinorUnits, authorisation.merchantName,
        )
    }

    private fun creditRefund(
        authorisation: CardAuthorisationEntity,
        notify: CardRefundNotify,
        transactionId: UUID,
    ) {
        val accountId = requireNotNull(authorisation.accountId) {
            "SETTLED authorisation ${authorisation.authorisationId} has no account_id; cannot refund"
        }
        ledgerClient.postTransaction(
            cardServiceTokenClient.getToken(),
            PostTransactionRequest(
                transactionId = transactionId,
                type = "CARD_REFUND",
                initiatedById = authorisation.accountHolderId ?: authorisation.authorisationId,
                postedAt = timeService.now().toString(),
                description = authorisation.merchantName,
                entries = listOf(
                    LedgerEntryRequest(
                        accountId = accountId,
                        amountMinorUnits = notify.refundAmountMinorUnits,
                        counterpartyId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                        description = authorisation.merchantName,
                        merchantName = authorisation.merchantName,
                    ),
                    LedgerEntryRequest(
                        accountId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                        amountMinorUnits = -notify.refundAmountMinorUnits,
                        counterpartyId = accountId,
                        description = authorisation.merchantName,
                    ),
                ),
            ),
        )
    }
}
