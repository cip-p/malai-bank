package com.malai.sqs.payments

import com.malai.sqs.QueueCatalog
import com.malai.sqs.QueueDefinition
import org.springframework.stereotype.Component


object PaymentsQueues {
    const val FPS_COMMAND_SUBMIT = "payments-fps-command-submit"
    const val FPS_COMMAND_RETURN = "payments-fps-command-return"
    const val FPS_EVENT_SETTLED = "payments-fps-event-settled"
    const val FPS_EVENT_RETURNED = "payments-fps-event-returned"
    const val FPS_EVENT_RECEIVED = "payments-fps-event-received"
    const val SWIFT_COMMAND_SUBMIT = "payments-swift-command-submit"
    const val SWIFT_EVENT_SETTLED = "payments-swift-event-settled"
    const val BACS_EVENT_RECEIVED = "payments-bacs-event-received"
    const val BACS_EVENT_COLLECTION = "payments-bacs-event-collection"
    const val BACS_COMMAND_RETURN = "payments-bacs-command-return"

    val ALL: List<String> = listOf(
        FPS_COMMAND_SUBMIT,
        FPS_COMMAND_RETURN,
        FPS_EVENT_SETTLED,
        FPS_EVENT_RETURNED,
        FPS_EVENT_RECEIVED,
        SWIFT_COMMAND_SUBMIT,
        SWIFT_EVENT_SETTLED,
        BACS_EVENT_RECEIVED,
        BACS_EVENT_COLLECTION,
        BACS_COMMAND_RETURN,
    )
}

@Component
class PaymentsQueueCatalog : QueueCatalog {
    override fun definitions(): List<QueueDefinition> = PaymentsQueues.ALL.map(::QueueDefinition)
}
