package com.malai.bacsgateway

import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.messages.BacsInboundNotify
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.payments.PaymentsQueues
import com.malai.sqs.tracking.withMalaiTracking
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BacsEventProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishReceived(event: BacsInboundNotify) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.BACS_EVENT_RECEIVED)
        sqsTemplate.send<BacsInboundNotify> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.endToEndId.toString())
                .messageDeduplicationId(event.endToEndId.toString())
                .withMalaiTracking()
        }
        log.info(
            "bacs gateway: produced received event endToEndId={} beneficiary={}/{} amount={}",
            event.endToEndId, event.beneficiarySortCode, event.beneficiaryAccountNumber, event.amountMinorUnits,
        )
    }

    fun publishCollection(event: BacsCollectionNotify) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.BACS_EVENT_COLLECTION)
        sqsTemplate.send<BacsCollectionNotify> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.endToEndId.toString())
                .messageDeduplicationId(event.endToEndId.toString())
                .withMalaiTracking()
        }
        log.info(
            "bacs gateway: produced collection event endToEndId={} debtor={}/{} creditor={}/{} amount={}",
            event.endToEndId, event.debtorSortCode, event.debtorAccountNumber,
            event.creditorSortCode, event.creditorAccountNumber, event.amountMinorUnits,
        )
    }
}
