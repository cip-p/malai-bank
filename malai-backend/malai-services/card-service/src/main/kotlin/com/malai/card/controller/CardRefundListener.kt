package com.malai.card.controller

import com.malai.card.messages.CardRefundNotify
import com.malai.card.service.CardRefundService
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Component

@Component
class CardRefundListener(private val cardRefundService: CardRefundService) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}cards-scheme-event-refunded.fifo")
    fun onRefunded(message: CardRefundNotify) {
        cardRefundService.refund(message)
    }
}
