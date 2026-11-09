package com.malai.payments.scheme

import com.malai.payments.scheme.producer.FpsReturnCommandProducer
import com.malai.client.malai.accounts.lookup.AccountLookupClient
import com.malai.commons.TimeService
import com.malai.payments.service.PaymentsServiceTokenClient
import com.malai.payments.dedup.SqsMessageDedupEntity
import com.malai.payments.dedup.SqsMessageDedupId
import com.malai.payments.dedup.SqsMessageDedupRepository
import com.malai.payments.entity.Rail
import com.malai.payments.messages.BacsInboundNotify
import com.malai.payments.messages.FpsInboundNotify
import com.malai.payments.messages.FpsInboundReturned
import com.malai.payments.messages.InboundNotify
import com.malai.sqs.payments.PaymentsQueues
import com.malai.payments.messages.ReturnReason
import com.malai.payments.service.PaymentLifecycleService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class InboundPaymentProcessor(
    private val dedupRepository: SqsMessageDedupRepository,
    private val accountLookupClient: AccountLookupClient,
    private val paymentsServiceTokenClient: PaymentsServiceTokenClient,
    private val paymentLifecycleService: PaymentLifecycleService,
    private val fpsReturnCommandProducer: FpsReturnCommandProducer,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(message: FpsInboundNotify) {
        processInternal(message, Rail.FPS, FPS_MESSAGE_TYPE)
    }

    @Transactional
    fun process(message: BacsInboundNotify) {
        processInternal(message, Rail.BACS_DIRECT_CREDIT, BACS_MESSAGE_TYPE)
    }

    private fun processInternal(message: InboundNotify, rail: Rail, messageType: String) {
        val dedupId = SqsMessageDedupId(
            messageType = messageType,
            endToEndId = message.endToEndId,
        )
        if (dedupRepository.existsById(dedupId)) {
            log.info(
                "{} deduplicated: endToEndId={} beneficiary={}/{}",
                messageType, message.endToEndId, message.beneficiarySortCode, message.beneficiaryAccountNumber,
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
            message.beneficiarySortCode,
            message.beneficiaryAccountNumber,
        )

        if (account == null) {
            fpsReturnCommandProducer.publish(
                FpsInboundReturned(
                    endToEndId = message.endToEndId,
                    beneficiarySortCode = message.beneficiarySortCode,
                    beneficiaryAccountNumber = message.beneficiaryAccountNumber,
                    reason = ReturnReason.INVALID_ACCOUNT,
                ),
            )
            log.info(
                "{}: unknown beneficiary, returned INVALID_ACCOUNT endToEndId={} beneficiary={}/{}",
                messageType, message.endToEndId, message.beneficiarySortCode, message.beneficiaryAccountNumber,
            )
            return
        }

        paymentLifecycleService.creditBeneficiary(
            message = message,
            rail = rail,
            beneficiaryAccountId = account.accountId,
            beneficiaryAccountHolderId = account.accountHolderId,
        )
    }

    companion object {
        const val FPS_MESSAGE_TYPE = PaymentsQueues.FPS_EVENT_RECEIVED
        const val BACS_MESSAGE_TYPE = PaymentsQueues.BACS_EVENT_RECEIVED
    }
}
