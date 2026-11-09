package com.malai.test.acceptance.kensa.interaction

import com.malai.commons.tracking.TestId
import com.malai.test.acceptance.kensa.CapturedInteractionHolder
import dev.kensa.state.CapturedInteractionBuilder
import dev.kensa.state.Party

data class SqsInteraction(
    val queueBaseName: String,
    val bodyJson: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Records SQS queue traffic onto the current test's Kensa sequence diagram, classified by queue
 * name into per-scheme lanes (FPS / SWIFT / BACS) via [SqsClassifier] — the queue-based analogue of
 * [WireMockInteractionCapturer] for HTTP. A `command-*` queue renders bank → scheme; an `event-*`
 * queue renders scheme → bank.
 */
object SqsInteractionCapturer {

    fun captureCommand(
        testId: TestId,
        bankParty: Party,
        queueBaseName: String,
        bodyJson: String,
        headers: Map<String, String> = emptyMap(),
    ) = capturePublish(testId, bankParty, queueBaseName, bodyJson, headers)

    fun captureEvent(
        testId: TestId,
        bankParty: Party,
        queueBaseName: String,
        bodyJson: String,
        headers: Map<String, String> = emptyMap(),
    ) = captureConsume(testId, bankParty, queueBaseName, bodyJson, headers)

    fun capturePublish(
        testId: TestId,
        producer: Party,
        queueBaseName: String,
        bodyJson: String,
        headers: Map<String, String> = emptyMap(),
    ) = capture(testId, from = producer, to = SqsClassifier.party(queueBaseName), queueBaseName, bodyJson, headers)

    fun captureConsume(
        testId: TestId,
        consumer: Party,
        queueBaseName: String,
        bodyJson: String,
        headers: Map<String, String> = emptyMap(),
    ) = capture(testId, from = SqsClassifier.party(queueBaseName), to = consumer, queueBaseName, bodyJson, headers)

    private fun capture(
        testId: TestId,
        from: Party,
        to: Party,
        queueBaseName: String,
        bodyJson: String,
        headers: Map<String, String>,
    ) {
        val interactions = CapturedInteractionHolder[testId] ?: return
        interactions.capture(
            CapturedInteractionBuilder.from(from)
                .to(to)
                .with(SqsInteraction(queueBaseName, bodyJson, headers), SqsClassifier.describe(queueBaseName))
        )
    }
}

/**
 * Project the headers of an inbound SQS [org.springframework.messaging.Message] onto a clean
 * `Map<String, String>` suitable for rendering on the Kensa sequence diagram. Drops Spring's
 * `id`/`timestamp` framework metadata and the noisy SQS plumbing headers (queue URL, receipt
 * handle, visibility timeout, …), and strips the `Sqs_Msa_` prefix off SQS message-system
 * attributes (`MessageGroupId`, `MessageDeduplicationId`, …) so the rendered key matches what
 * a producer would set.
 */
fun renderableSqsHeaders(headers: Map<String, Any?>): Map<String, String> =
    headers.entries
        .filter { it.value != null }
        .filterNot { it.key in NOISY_SQS_HEADERS }
        .associate { it.key.removePrefix("Sqs_Msa_") to it.value.toString() }

private val NOISY_SQS_HEADERS = setOf(
    "id",
    "timestamp",
    "Sqs_QueueName",
    "Sqs_QueueUrl",
    "Sqs_ReceiptHandle",
    "Sqs_VisibilityTimeout",
    "Sqs_SourceData",
    "Sqs_ReceivedAt",
    "Sqs_Delay",
    "Sqs_QueueAttributes",
    "Sqs_ReceiveRequestAttemptId",
)
