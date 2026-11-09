package com.malai.sqs.tracking

import tools.jackson.databind.ObjectMapper
import com.malai.commons.tracking.KensaEventEnvelope
import com.malai.commons.tracking.KensaEventPublisher
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.util.UUID

/**
 * SQS-backed [KensaEventPublisher] that serialises envelopes as JSON and forwards them to
 * `kensa-events.fifo` with `messageGroupId = testId` so per-test ordering is preserved at the
 * consumer side.
 *
 * Synchronous (`.get()`): each publish blocks until SQS confirms receipt before returning. This
 * is what guarantees that the order of publish *calls* on a given thread matches the order of
 * arrival at the queue — which is what FIFO ordering preserves for the consumer. With fire-and-
 * forget, rapid back-to-back publishes from the same caller thread were getting reordered by the
 * SDK's Netty event-loop concurrency, breaking the diagram order.
 *
 * The `.get()` block runs on whichever thread invoked `publish`. For the HTTP capture path that's
 * the caller's HC5 exec thread — safe to block. For the SQS capture path (the
 * [KensaSqsExecutionInterceptor]'s `afterExecution`), it runs on an SDK Netty callback thread:
 * the awaited send dispatches to another Netty thread in the same pool, so this isn't a self-
 * deadlock as long as the pool has more than one thread (the AWS SDK default).
 *
 * Failures are logged and swallowed — observability must not break the surrounding business
 * operation, per the [KensaEventPublisher] contract.
 */
class KensaSqsEventPublisher(
    private val sqsAsyncClientProvider: () -> SqsAsyncClient,
    private val kensaQueueResolver: KensaQueueResolver,
    private val objectMapper: ObjectMapper,
) : KensaEventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(envelope: KensaEventEnvelope) {
        val queueUrl = kensaQueueResolver.queueUrl() ?: run {
            log.debug("kensa-events queue url not resolvable yet; dropping envelope kind={}", envelope.kind)
            return
        }
        val body = objectMapper.writeValueAsString(envelope)
        val dedupKey = UUID.randomUUID().toString()
        try {
            sqsAsyncClientProvider().sendMessage { send ->
                send
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageGroupId(envelope.testId)
                    .messageDeduplicationId(dedupKey)
            }.get()
        } catch (e: Exception) {
            log.warn("failed to publish kensa envelope kind={}: {}", envelope.kind, e.message)
        }
    }
}
