package com.malai.payments.support

import com.malai.commons.tracking.TestId
import com.malai.sqs.payments.PaymentsQueues
import com.malai.payments.messages.SwiftOutboundSubmit
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.renderableSqsHeaders
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message

class SwiftCommandCaptureListener {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-swift-command-submit.fifo")
    fun capture(message: Message<SwiftOutboundSubmit>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        val submit = message.payload
        CapturedSwiftCommands.record(testId, submit)
        SqsInteractionCapturer.captureCommand(
            TestId(testId),
            MalaiParty.PaymentsService,
            PaymentsQueues.SWIFT_COMMAND_SUBMIT,
            testingObjectMapper.writeValueAsString(submit),
            headers = renderableSqsHeaders(message.headers),
        )
    }
}
