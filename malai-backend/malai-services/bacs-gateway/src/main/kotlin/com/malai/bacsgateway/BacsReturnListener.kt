package com.malai.bacsgateway

import com.malai.payments.messages.BacsCollectionReturn
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class BacsReturnListener(private val schemeClient: BacsSchemeClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-bacs-command-return.fifo")
    fun onReturn(message: Message<BacsCollectionReturn>) {
        val returned = message.payload
        schemeClient.returnCollection(BacsSchemeTranslator.toSchemeCollectionReturn(returned))
        log.info(
            "bacs gateway: returned collection to scheme endToEndId={} creditor={}/{} reason={}",
            returned.endToEndId, returned.creditorSortCode, returned.creditorAccountNumber, returned.reason,
        )
    }
}
