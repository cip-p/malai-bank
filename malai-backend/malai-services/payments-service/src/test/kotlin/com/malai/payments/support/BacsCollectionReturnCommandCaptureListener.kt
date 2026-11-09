package com.malai.payments.support

import com.malai.commons.tracking.TestId
import com.malai.payments.messages.BacsCollectionReturn
import com.malai.sqs.payments.PaymentsQueues
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.interaction.renderableSqsHeaders
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message

class BacsCollectionReturnCommandCaptureListener {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-bacs-command-return.fifo")
    fun capture(message: Message<BacsCollectionReturn>) {
        val testId = message.headers[TestId.HEADER]?.toString() ?: return
        val returned = message.payload
        CapturedBacsCollectionReturns.record(testId, returned)
        SqsInteractionCapturer.captureCommand(
            TestId(testId),
            MalaiParty.PaymentsService,
            PaymentsQueues.BACS_COMMAND_RETURN,
            testingObjectMapper.writeValueAsString(returned),
            headers = renderableSqsHeaders(message.headers),
        )
    }
}
