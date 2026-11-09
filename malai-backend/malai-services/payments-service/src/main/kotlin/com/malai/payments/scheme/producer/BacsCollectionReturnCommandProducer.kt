package com.malai.payments.scheme.producer

import com.malai.payments.messages.BacsCollectionReturn
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.payments.PaymentsQueues
import com.malai.sqs.tracking.withMalaiTracking
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BacsCollectionReturnCommandProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(returned: BacsCollectionReturn) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.BACS_COMMAND_RETURN)
        sqsTemplate.send<BacsCollectionReturn> { options ->
            options
                .queue(queueName)
                .payload(returned)
                .messageGroupId(returned.endToEndId.toString())
                .messageDeduplicationId(returned.endToEndId.toString())
                .withMalaiTracking()
        }
        log.info("bacs collection return published: endToEndId={} reason={}", returned.endToEndId, returned.reason)
    }
}
