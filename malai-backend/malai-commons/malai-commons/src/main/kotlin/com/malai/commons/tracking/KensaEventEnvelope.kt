package com.malai.commons.tracking

/**
 * Wire-level payload carried over `kensa-events.fifo` in end-to-end tests so containers can
 * publish "I just observed this interaction" facts to the test JVM, which would otherwise be
 * blind to inter-container SQS traffic. The acceptance-test infrastructure writes directly to
 * the in-process CapturedInteractionHolder; this envelope is the same data routed over the
 * wire when the writer and the renderer are in different JVMs.
 *
 * Ordering on the receiving side is preserved by sending each envelope with
 * `messageGroupId = testId` on a FIFO queue, so all envelopes for one test arrive in producer
 * order regardless of how many containers contribute concurrently.
 */
data class KensaEventEnvelope(
    val testId: String,
    val kind: KensaEventKind,
    val tsEpochMillis: Long,
    val fromParty: String? = null,
    val toParty: String? = null,
    val queueBaseName: String? = null,
    val description: String? = null,
    val bodyJson: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val httpMethod: String? = null,
    val httpUrl: String? = null,
    val httpStatus: Int? = null,
)

enum class KensaEventKind {
    SQS_PUBLISH,
    SQS_CONSUME,
    HTTP_REQUEST,
    HTTP_RESPONSE,
    TEST_ENDED,
}
