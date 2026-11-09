package com.malai.payments.scheme.consumer

import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.scheme.DirectDebitCollectionProcessor
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class BacsCollectionReceivedListener(
    private val processor: DirectDebitCollectionProcessor,
) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-bacs-event-collection.fifo")
    fun onCollection(message: Message<BacsCollectionNotify>) {
        processor.process(message.payload)
    }
}
