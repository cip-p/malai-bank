package com.malai.card.controller

import com.malai.card.messages.CardReversalNotify
import com.malai.card.service.CardReversalService
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Component

@Component
class CardReversalListener(private val cardReversalService: CardReversalService) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}cards-scheme-event-reversed.fifo")
    fun onReversed(message: CardReversalNotify) {
        cardReversalService.reverse(message)
    }
}
