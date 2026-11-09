package com.malai.payments.scheme.consumer

import com.malai.payments.messages.FpsInboundNotify
import com.malai.payments.scheme.InboundPaymentProcessor
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class FpsInboundReceivedListener(
    private val processor: InboundPaymentProcessor,
) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-fps-event-received.fifo")
    fun onReceived(message: Message<FpsInboundNotify>) {
        processor.process(message.payload)
    }
}
