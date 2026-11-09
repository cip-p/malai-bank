package com.malai.fpsgateway.support

import com.malai.commons.tracking.TestId
import com.malai.payments.messages.FpsInboundNotify
import com.malai.payments.messages.FpsOutboundReturned
import com.malai.payments.messages.FpsOutboundSettled
import com.malai.sqs.payments.PaymentsQueues
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.renderableSqsHeaders
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message

class FpsEventCaptureListener(private val events: CapturedFpsEvents) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-fps-event-settled.fifo")
    fun settled(message: Message<FpsOutboundSettled>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordSettled(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.FpsGateway,
            PaymentsQueues.FPS_EVENT_SETTLED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-fps-event-returned.fifo")
    fun returned(message: Message<FpsOutboundReturned>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordReturned(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.FpsGateway,
            PaymentsQueues.FPS_EVENT_RETURNED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-fps-event-received.fifo")
    fun received(message: Message<FpsInboundNotify>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordReceived(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.FpsGateway,
            PaymentsQueues.FPS_EVENT_RECEIVED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }
}
