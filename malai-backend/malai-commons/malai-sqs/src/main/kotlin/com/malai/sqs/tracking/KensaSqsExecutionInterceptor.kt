package com.malai.sqs.tracking

import com.malai.commons.tracking.KensaEventEnvelope
import com.malai.commons.tracking.KensaEventKind
import com.malai.commons.tracking.KensaEventPublisher
import com.malai.commons.tracking.TestId
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import java.util.concurrent.atomic.AtomicReference

/**
 * Hooks the SDK pipeline to emit Kensa envelopes for each SQS interaction:
 *
 * - `afterExecution` on SendMessage: SQS_PUBLISH envelope carrying the producer source from
 *   the message's `X-Malai-Source` attribute (set explicitly on the caller thread by each
 *   producer — see [MalaiTrackingSendOptions], since the SDK async client hops to its own
 *   executor before interceptors fire and ThreadLocal-based stamping here wouldn't work).
 * - `afterExecution` on ReceiveMessage: SQS_CONSUME envelope per returned message, carrying
 *   the consumer service derived from the queue→consumer map (no ThreadLocal at SDK level).
 *
 * The kensa queue itself is excluded from both directions to prevent recursion.
 */
class KensaSqsExecutionInterceptor(
    private val publisher: KensaEventPublisher,
    private val queueBaseNameMatcher: QueueBaseNameMatcher,
    private val queueBaseNameToConsumerService: Map<String, String>,
) : ExecutionInterceptor {

    override fun afterExecution(
        context: Context.AfterExecution,
        executionAttributes: ExecutionAttributes,
    ) {
        when (val request = context.request()) {
            is SendMessageRequest -> handleSend(request, context.response() as? SendMessageResponse)
            is ReceiveMessageRequest -> handleReceive(request, context.response() as? ReceiveMessageResponse)
            else -> Unit
        }
    }

    private fun handleSend(request: SendMessageRequest, response: SendMessageResponse?) {
        if (response == null) return
        if (isKensaQueue(request.queueUrl())) return
        val testId = request.messageAttributes()[TestId.HEADER]?.stringValue() ?: return
        val baseName = queueBaseNameMatcher.baseNameOf(request.queueUrl()) ?: return
        val source = request.messageAttributes()[MALAI_SOURCE_HEADER]?.stringValue()
        publisher.publish(
            KensaEventEnvelope(
                testId = testId,
                kind = KensaEventKind.SQS_PUBLISH,
                tsEpochMillis = System.currentTimeMillis(),
                fromParty = source,
                queueBaseName = baseName,
                bodyJson = request.messageBody(),
                headers = headerMap(request.messageAttributes()),
            )
        )
    }

    private fun handleReceive(request: ReceiveMessageRequest, response: ReceiveMessageResponse?) {
        if (response == null) return
        if (isKensaQueue(request.queueUrl())) return
        val baseName = queueBaseNameMatcher.baseNameOf(request.queueUrl()) ?: return
        val consumerService = queueBaseNameToConsumerService[baseName]
        for (message in response.messages()) {
            val testId = message.messageAttributes()[TestId.HEADER]?.stringValue() ?: continue
            publisher.publish(
                KensaEventEnvelope(
                    testId = testId,
                    kind = KensaEventKind.SQS_CONSUME,
                    tsEpochMillis = System.currentTimeMillis(),
                    toParty = consumerService,
                    queueBaseName = baseName,
                    bodyJson = message.body(),
                    headers = headerMap(message.messageAttributes()),
                )
            )
        }
    }

    private fun headerMap(attrs: Map<String, MessageAttributeValue>): Map<String, String> =
        attrs
            .filter { it.value.stringValue() != null }
            .mapValues { it.value.stringValue()!! }

    private fun isKensaQueue(url: String): Boolean =
        url.endsWith("${KensaEventQueue.BASE_NAME}.fifo")

}

/**
 * Resolves the full URL of `kensa-events.fifo` lazily on first use (and caches it). Lazy
 * because the queue is created at startup by [com.malai.sqs.QueueProvisioner], which may run
 * after the SqsAsyncClient bean is built (the interceptor is wired into the client at that
 * earlier point).
 */
class KensaQueueResolver(
    private val sqsAsyncClientProvider: () -> SqsAsyncClient,
    private val physicalQueueName: () -> String,
) {
    private val cached = AtomicReference<String?>(null)

    fun queueUrl(): String? = cached.get() ?: try {
        sqsAsyncClientProvider().getQueueUrl { it.queueName(physicalQueueName()) }
            .get()
            .queueUrl()
            .also(cached::set)
    } catch (_: Exception) {
        null
    }
}

/**
 * Resolves an SQS queue URL (full or just the queue name) to a known base name by suffix-matching
 * against the contributed [MalaiSourceQueues] beans. Returns null if no match — that traffic is
 * not declared and therefore not on the diagram.
 */
class QueueBaseNameMatcher(private val knownBaseNames: List<String>) {
    private val sortedDesc = knownBaseNames.sortedByDescending { it.length }

    fun baseNameOf(queueUrl: String): String? {
        val last = queueUrl.substringAfterLast('/').removeSuffix(".fifo")
        return sortedDesc.firstOrNull { last.endsWith(it) }
    }
}
