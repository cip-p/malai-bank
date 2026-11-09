package com.malai.cardgateway.support

import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.messages.CardRefundNotify
import com.malai.card.messages.CardReversalNotify
import com.malai.commons.tracking.TestId
import com.malai.sqs.cards.CardsQueues
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.renderableSqsHeaders
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message

class CardEventCaptureListener(private val events: CapturedCardEvents) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}cards-scheme-event-presented.fifo")
    fun presented(message: Message<CardPresentmentNotify>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordPresented(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.CardGateway,
            CardsQueues.SCHEME_EVENT_PRESENTED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }

    @SqsListener("\${malai.sqs.queue-name-prefix:}cards-scheme-event-reversed.fifo")
    fun reversed(message: Message<CardReversalNotify>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordReversed(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.CardGateway,
            CardsQueues.SCHEME_EVENT_REVERSED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }

    @SqsListener("\${malai.sqs.queue-name-prefix:}cards-scheme-event-refunded.fifo")
    fun refunded(message: Message<CardRefundNotify>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordRefunded(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.CardGateway,
            CardsQueues.SCHEME_EVENT_REFUNDED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }
}
