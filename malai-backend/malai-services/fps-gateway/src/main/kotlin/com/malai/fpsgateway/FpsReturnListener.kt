package com.malai.fpsgateway

import com.malai.payments.messages.FpsInboundReturned
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class FpsReturnListener(private val schemeClient: FpsSchemeClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-fps-command-return.fifo")
    fun onReturn(message: Message<FpsInboundReturned>) {
        val returned = message.payload
        schemeClient.returnToSender(FpsSchemeTranslator.toSchemeReturnToSender(returned))
        log.info(
            "fps gateway: returned-to-sender to scheme endToEndId={} beneficiary={}/{} reason={}",
            returned.endToEndId, returned.beneficiarySortCode, returned.beneficiaryAccountNumber, returned.reason,
        )
    }
}
