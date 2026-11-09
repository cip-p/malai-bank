package com.malai.card.controller

import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.service.CardPresentmentService
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Component

@Component
class CardPresentmentListener(private val cardPresentmentService: CardPresentmentService) {

    @SqsListener("\${malai.sqs.queue-name-prefix:}cards-scheme-event-presented.fifo")
    fun onPresented(message: CardPresentmentNotify) {
        cardPresentmentService.present(message)
    }
}
