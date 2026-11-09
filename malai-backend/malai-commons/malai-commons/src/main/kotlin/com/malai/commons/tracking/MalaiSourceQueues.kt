package com.malai.commons.tracking

/**
 * Declarative description of which SQS queue base names a logical malai-service consumes from.
 * Each service module contributes one (via `@Bean fun fooServiceSourceQueues()`); the SQS
 * interceptor in `malai-sqs` collects them and, for each inbound message, populates
 * [MalaiSourceContext] with the matching [serviceName] for the duration of message handling —
 * by longest-suffix wins.
 *
 * The [serviceName] values are observable: they end up on the wire as the `X-Malai-Source`
 * header of every outbound call made while a message is being processed, and they drive party
 * attribution on the Kensa sequence diagram in acceptance / e2e tests. Keep them aligned with
 * the values used in [MalaiSourceRoutes] for the same service.
 *
 * [queueBaseNames] are the unprefixed names (e.g. `payments-fps-command-submit`); the
 * interceptor accounts for the configurable `malai.sqs.queue-name-prefix` and the `.fifo`
 * suffix when matching against the actual queue name on the message header.
 */
data class MalaiSourceQueues(
    val serviceName: String,
    val queueBaseNames: List<String>,
)
