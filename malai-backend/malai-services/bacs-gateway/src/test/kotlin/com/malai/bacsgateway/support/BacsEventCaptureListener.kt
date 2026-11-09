package com.malai.bacsgateway.support

import com.malai.commons.tracking.TestId
import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.messages.BacsInboundNotify
import com.malai.sqs.payments.PaymentsQueues
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.renderableSqsHeaders
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message

class BacsEventCaptureListener(private val events: CapturedBacsEvents) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-bacs-event-received.fifo")
    fun received(message: Message<BacsInboundNotify>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordReceived(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.BacsGateway,
            PaymentsQueues.BACS_EVENT_RECEIVED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-bacs-event-collection.fifo")
    fun collection(message: Message<BacsCollectionNotify>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordCollection(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.BacsGateway,
            PaymentsQueues.BACS_EVENT_COLLECTION,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }
}
