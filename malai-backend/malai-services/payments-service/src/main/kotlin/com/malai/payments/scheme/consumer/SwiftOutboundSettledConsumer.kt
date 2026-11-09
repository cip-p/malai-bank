package com.malai.payments.scheme.consumer

import com.malai.commons.TimeService
import com.malai.payments.dedup.SqsMessageDedupEntity
import com.malai.payments.dedup.SqsMessageDedupId
import com.malai.payments.dedup.SqsMessageDedupRepository
import com.malai.sqs.payments.PaymentsQueues
import com.malai.payments.messages.SwiftOutboundSettled
import com.malai.payments.service.PaymentLifecycleService
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SwiftOutboundSettledConsumer(
    private val dedupRepository: SqsMessageDedupRepository,
    private val paymentLifecycleService: PaymentLifecycleService,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @SqsListener("\${malai.sqs.queue-name-prefix:}payments-swift-event-settled.fifo")
    @Transactional
    fun process(message: SwiftOutboundSettled) {
        val dedupId = SqsMessageDedupId(
            messageType = MESSAGE_TYPE,
            endToEndId = message.endToEndId,
        )
        if (dedupRepository.existsById(dedupId)) {
            log.info(
                "swift outbound settled deduplicated: endToEndId={} paymentId={}",
                message.endToEndId, message.paymentId,
            )
            return
        }
        dedupRepository.save(
            SqsMessageDedupEntity(
                id = dedupId,
                processedAt = timeService.now(),
            ),
        )
        paymentLifecycleService.transitionToSettled(message.paymentId)
    }

    companion object {
        const val MESSAGE_TYPE = PaymentsQueues.SWIFT_EVENT_SETTLED
    }
}
