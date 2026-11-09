package com.malai.sqs.tracking

import com.malai.commons.tracking.MalaiSourceContext
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import io.awspring.cloud.sqs.operations.SqsSendOptions

const val MALAI_SOURCE_HEADER: String = "X-Malai-Source"

/**
 * Stamps the current `TestId` and `X-Malai-Source` ThreadLocal values as headers on the
 * outbound SQS message. Called inside each producer's `sqsTemplate.send { ... }` lambda so
 * it runs on the caller thread — the AWS SDK v2 async client hops to its own executor before
 * `ExecutionInterceptor.modifyRequest` fires, so doing it there silently no-ops.
 *
 * Headers are mapped to SDK message attributes by the awspring header mapper, where the
 * consumer-side `KensaSqsExecutionInterceptor` reads them to attribute the producer party
 * on the rendered Kensa sequence diagram.
 */
fun <T : Any> SqsSendOptions<T>.withMalaiTracking(): SqsSendOptions<T> {
    TestIdContext.valueOrNull?.let { header(TestId.HEADER, it.value) }
    MalaiSourceContext.valueOrNull?.let { header(MALAI_SOURCE_HEADER, it.name) }
    return this
}
