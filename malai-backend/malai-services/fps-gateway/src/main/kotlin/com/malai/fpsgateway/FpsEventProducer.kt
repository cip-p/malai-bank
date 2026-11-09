package com.malai.fpsgateway

import com.malai.payments.messages.FpsInboundNotify
import com.malai.payments.messages.FpsOutboundReturned
import com.malai.payments.messages.FpsOutboundSettled
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.payments.PaymentsQueues
import com.malai.sqs.tracking.withMalaiTracking
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FpsEventProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishSettled(event: FpsOutboundSettled) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_EVENT_SETTLED)
        sqsTemplate.send<FpsOutboundSettled> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.paymentId.toString())
                .messageDeduplicationId(event.paymentId.toString())
                .withMalaiTracking()
        }
        log.info("fps gateway: produced settled event paymentId={} endToEndId={}", event.paymentId, event.endToEndId)
    }

    fun publishReturned(event: FpsOutboundReturned) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_EVENT_RETURNED)
        sqsTemplate.send<FpsOutboundReturned> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.paymentId.toString())
                .messageDeduplicationId(event.paymentId.toString())
                .withMalaiTracking()
        }
        log.info(
            "fps gateway: produced returned event paymentId={} endToEndId={} reason={}",
            event.paymentId, event.endToEndId, event.reason,
        )
    }

    fun publishReceived(event: FpsInboundNotify) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_EVENT_RECEIVED)
        sqsTemplate.send<FpsInboundNotify> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.endToEndId.toString())
                .messageDeduplicationId(event.endToEndId.toString())
                .withMalaiTracking()
        }
        log.info(
            "fps gateway: produced received event endToEndId={} beneficiary={}/{} amount={}",
            event.endToEndId, event.beneficiarySortCode, event.beneficiaryAccountNumber, event.amountMinorUnits,
        )
    }
}
