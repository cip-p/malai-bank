package com.malai.swiftgateway

import com.malai.payments.messages.SwiftOutboundSubmit
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class SwiftSubmitListener(private val schemeClient: SwiftSchemeClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-swift-command-submit.fifo")
    fun onSubmit(message: Message<SwiftOutboundSubmit>) {
        val submit = message.payload
        val ack = schemeClient.submit(SwiftSchemeTranslator.toSchemeSubmit(submit))
        log.info(
            "swift gateway: submitted to scheme paymentId={} endToEndId={} ackStatus={}",
            submit.paymentId, submit.endToEndId, ack.status,
        )
    }
}
