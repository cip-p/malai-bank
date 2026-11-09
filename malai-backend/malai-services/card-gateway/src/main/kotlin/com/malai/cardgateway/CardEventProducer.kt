package com.malai.cardgateway

import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.messages.CardRefundNotify
import com.malai.card.messages.CardReversalNotify
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.cards.CardsQueues
import com.malai.sqs.tracking.withMalaiTracking
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CardEventProducer(
    private val sqsTemplate: SqsTemplate,
    private val props: MalaiSqsMessagingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishPresented(event: CardPresentmentNotify) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, CardsQueues.SCHEME_EVENT_PRESENTED)
        sqsTemplate.send<CardPresentmentNotify> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.schemeReference.toString())
                .messageDeduplicationId(event.schemeReference.toString())
                .withMalaiTracking()
        }
        log.info(
            "card gateway: produced presented event schemeReference={} merchant={} amount={}",
            event.schemeReference, event.merchantName, event.presentedAmountMinorUnits,
        )
    }

    fun publishReversed(event: CardReversalNotify) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, CardsQueues.SCHEME_EVENT_REVERSED)
        sqsTemplate.send<CardReversalNotify> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.schemeReference.toString())
                .messageDeduplicationId(event.schemeReference.toString())
                .withMalaiTracking()
        }
        log.info(
            "card gateway: produced reversed event schemeReference={}",
            event.schemeReference,
        )
    }

    fun publishRefunded(event: CardRefundNotify) {
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, CardsQueues.SCHEME_EVENT_REFUNDED)
        sqsTemplate.send<CardRefundNotify> { options ->
            options
                .queue(queueName)
                .payload(event)
                .messageGroupId(event.schemeReference.toString())
                .messageDeduplicationId(event.refundReference.toString())
                .withMalaiTracking()
        }
        log.info(
            "card gateway: produced refunded event schemeReference={} refundReference={} amount={}",
            event.schemeReference, event.refundReference, event.refundAmountMinorUnits,
        )
    }
}
