package com.malai.payments.scheme

import com.malai.client.malai.accounts.lookup.AccountLookupClient
import com.malai.commons.TimeService
import com.malai.payments.dedup.SqsMessageDedupEntity
import com.malai.payments.dedup.SqsMessageDedupId
import com.malai.payments.dedup.SqsMessageDedupRepository
import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.messages.BacsCollectionReturn
import com.malai.payments.messages.ReturnReason
import com.malai.payments.scheme.producer.BacsCollectionReturnCommandProducer
import com.malai.payments.service.PaymentLifecycleService
import com.malai.payments.service.PaymentsServiceTokenClient
import com.malai.sqs.payments.PaymentsQueues
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DirectDebitCollectionProcessor(
    private val dedupRepository: SqsMessageDedupRepository,
    private val accountLookupClient: AccountLookupClient,
    private val paymentsServiceTokenClient: PaymentsServiceTokenClient,
    private val paymentLifecycleService: PaymentLifecycleService,
    private val bacsCollectionReturnCommandProducer: BacsCollectionReturnCommandProducer,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(message: BacsCollectionNotify) {
        val dedupId = SqsMessageDedupId(
            messageType = COLLECTION_MESSAGE_TYPE,
            endToEndId = message.endToEndId,
        )
        if (dedupRepository.existsById(dedupId)) {
            log.info(
                "{} deduplicated: endToEndId={} debtor={}/{}",
                COLLECTION_MESSAGE_TYPE, message.endToEndId, message.debtorSortCode, message.debtorAccountNumber,
            )
            return
        }
        dedupRepository.save(
            SqsMessageDedupEntity(
                id = dedupId,
                processedAt = timeService.now(),
            ),
        )

        val account = accountLookupClient.resolveBySortCodeAndAccountNumber(
            paymentsServiceTokenClient.getToken(),
            message.debtorSortCode,
            message.debtorAccountNumber,
        )

        if (account == null) {
            bacsCollectionReturnCommandProducer.publish(returnFor(message, ReturnReason.INVALID_ACCOUNT))
            log.info(
                "{}: unknown target account, returned INVALID_ACCOUNT endToEndId={} debtor={}/{}",
                COLLECTION_MESSAGE_TYPE, message.endToEndId, message.debtorSortCode, message.debtorAccountNumber,
            )
            return
        }

        val settled = paymentLifecycleService.collectFromDebtor(
            message = message,
            debtorAccountId = account.accountId,
            debtorAccountHolderId = account.accountHolderId,
        )

        if (!settled) {
            bacsCollectionReturnCommandProducer.publish(returnFor(message, ReturnReason.INSUFFICIENT_FUNDS))
        }
    }

    private fun returnFor(message: BacsCollectionNotify, reason: ReturnReason) = BacsCollectionReturn(
        endToEndId = message.endToEndId,
        creditorSortCode = message.creditorSortCode,
        creditorAccountNumber = message.creditorAccountNumber,
        creditorName = message.creditorName,
        reason = reason,
    )

    companion object {
        const val COLLECTION_MESSAGE_TYPE = PaymentsQueues.BACS_EVENT_COLLECTION
    }
}
