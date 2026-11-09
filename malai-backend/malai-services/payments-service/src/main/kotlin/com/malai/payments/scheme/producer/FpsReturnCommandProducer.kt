package com.malai.payments.scheme.producer

import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.tracking.withMalaiTracking
import com.malai.payments.messages.FpsInboundReturned
import com.malai.sqs.payments.PaymentsQueues
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FpsReturnCommandProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(returned: FpsInboundReturned) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_COMMAND_RETURN)
        sqsTemplate.send<FpsInboundReturned> { options ->
            options
                .queue(queueName)
                .payload(returned)
                .messageGroupId(returned.endToEndId.toString())
                .messageDeduplicationId(returned.endToEndId.toString())
                .withMalaiTracking()
        }
        log.info("fps inbound return published: endToEndId={} reason={}", returned.endToEndId, returned.reason)
    }
}
