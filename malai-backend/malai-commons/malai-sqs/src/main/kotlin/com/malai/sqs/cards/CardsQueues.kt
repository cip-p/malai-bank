package com.malai.sqs.cards

import com.malai.sqs.QueueCatalog
import com.malai.sqs.QueueDefinition
import org.springframework.stereotype.Component

object CardsQueues {
    const val SCHEME_EVENT_PRESENTED = "cards-scheme-event-presented"
    const val SCHEME_EVENT_REVERSED = "cards-scheme-event-reversed"
    const val SCHEME_EVENT_REFUNDED = "cards-scheme-event-refunded"

    val ALL: List<String> = listOf(
        SCHEME_EVENT_PRESENTED,
        SCHEME_EVENT_REVERSED,
        SCHEME_EVENT_REFUNDED,
    )
}

@Component
class CardsQueueCatalog : QueueCatalog {
    override fun definitions(): List<QueueDefinition> = CardsQueues.ALL.map(::QueueDefinition)
}
