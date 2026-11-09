package com.malai.commons.tracking

/**
 * Production-side abstraction for emitting Kensa observability events without depending on the
 * transport. The real implementation lives in `malai-sqs` and publishes envelopes to
 * `kensa-events.fifo`; production builds that don't bundle the e2e profile see only
 * [NoopKensaEventPublisher], so the call sites compile and run unaffected.
 *
 * Implementations must be safe to call from arbitrary threads (request handlers, SDK callbacks,
 * SQS listener threads). Publishing should not throw — observability failures must not break
 * the surrounding business operation.
 */
interface KensaEventPublisher {
    fun publish(envelope: KensaEventEnvelope)
}

object NoopKensaEventPublisher : KensaEventPublisher {
    override fun publish(envelope: KensaEventEnvelope) {}
}
