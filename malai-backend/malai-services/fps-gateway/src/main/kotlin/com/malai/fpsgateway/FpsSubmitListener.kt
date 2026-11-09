package com.malai.fpsgateway

import com.malai.payments.messages.FpsOutboundSubmit
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class FpsSubmitListener(private val schemeClient: FpsSchemeClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-fps-command-submit.fifo")
    fun onSubmit(message: Message<FpsOutboundSubmit>) {
        val submit = message.payload
        val ack = schemeClient.submit(FpsSchemeTranslator.toSchemeSubmit(submit))
        log.info(
            "fps gateway: submitted to scheme paymentId={} endToEndId={} ackStatus={}",
            submit.paymentId, submit.endToEndId, ack.status,
        )
    }
}
