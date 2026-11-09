package com.malai.payments.scheme.consumer

import com.malai.payments.messages.BacsInboundNotify
import com.malai.payments.scheme.InboundPaymentProcessor
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class BacsInboundReceivedListener(
    private val processor: InboundPaymentProcessor,
) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-bacs-event-received.fifo")
    fun onReceived(message: Message<BacsInboundNotify>) {
        processor.process(message.payload)
    }
}
