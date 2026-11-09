package com.malai.swiftgateway

import com.malai.payments.messages.SwiftOutboundSettled
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.payments.PaymentsQueues
import com.malai.sqs.tracking.withMalaiTracking
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SwiftEventProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishSettled(event: SwiftOutboundSettled) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.SWIFT_EVENT_SETTLED)
        sqsTemplate.send<SwiftOutboundSettled> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.paymentId.toString())
                .messageDeduplicationId(event.paymentId.toString())
                .withMalaiTracking()
        }
        log.info("swift gateway: produced settled event paymentId={} endToEndId={}", event.paymentId, event.endToEndId)
    }
}
