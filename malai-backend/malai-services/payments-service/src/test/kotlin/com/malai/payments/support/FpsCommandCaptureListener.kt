package com.malai.payments.support

import com.malai.commons.tracking.TestId
import com.malai.payments.messages.FpsOutboundSubmit
import com.malai.sqs.payments.PaymentsQueues
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.renderableSqsHeaders
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message

class FpsCommandCaptureListener {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-fps-command-submit.fifo")
    fun capture(message: Message<FpsOutboundSubmit>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        val submit = message.payload
        CapturedFpsCommands.record(testId, submit)
        SqsInteractionCapturer.captureCommand(
            TestId(testId),
            MalaiParty.PaymentsService,
            PaymentsQueues.FPS_COMMAND_SUBMIT,
            testingObjectMapper.writeValueAsString(submit),
            headers = renderableSqsHeaders(message.headers),
        )
    }
}
