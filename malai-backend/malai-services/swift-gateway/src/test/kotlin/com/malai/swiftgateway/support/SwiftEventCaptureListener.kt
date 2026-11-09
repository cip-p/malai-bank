package com.malai.swiftgateway.support

import com.malai.commons.tracking.TestId
import com.malai.payments.messages.SwiftOutboundSettled
import com.malai.sqs.payments.PaymentsQueues
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.renderableSqsHeaders
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message

class SwiftEventCaptureListener(private val events: CapturedSwiftEvents) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-swift-event-settled.fifo")
    fun settled(message: Message<SwiftOutboundSettled>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        events.recordSettled(testId, message.payload)
        SqsInteractionCapturer.captureEvent(
            TestId(testId),
            MalaiParty.SwiftGateway,
            PaymentsQueues.SWIFT_EVENT_SETTLED,
            testingObjectMapper.writeValueAsString(message.payload),
            headers = renderableSqsHeaders(message.headers),
        )
    }
}
