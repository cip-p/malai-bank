package com.malai.sqs.tracking

import com.malai.commons.tracking.MalaiSource
import com.malai.commons.tracking.MalaiSourceContext
import com.malai.commons.tracking.MalaiSourceQueues
import io.awspring.cloud.sqs.listener.SqsHeaders
import io.awspring.cloud.sqs.listener.interceptor.MessageInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message

/**
 * Installs a SQS [MessageInterceptor] that maps the inbound queue name to a logical malai-service
 * via the [MalaiSourceQueues] beans contributed by each service module, and populates
 * [MalaiSourceContext] for the duration of message handling. Downstream outbound calls
 * (AbstractMalaiClient HTTP, MalaiSourceAwsSdkInterceptorConfig AWS SDK) then tag
 * `X-Malai-Source` with the logical service rather than the deployment unit.
 *
 * Always-on (no `@Profile` gate): the same source resolution applies to production logs and
 * traces, not only to acceptance / e2e tests. Mirrors `MalaiSourceServletFilterConfig` for the
 * HTTP path.
 */
@Configuration
class MalaiSourceSqsInterceptorConfig(
    private val queues: List<MalaiSourceQueues>,
) {

    @Bean
    fun malaiSourceSqsInterceptor(): MessageInterceptor<Any> {
        val baseNameToService = queues
            .flatMap { route -> route.queueBaseNames.map { it to route.serviceName } }
            .sortedByDescending { it.first.length }
        return object : MessageInterceptor<Any> {
            override fun intercept(message: Message<Any>): Message<Any> {
                val queueName = message.headers[SqsHeaders.SQS_QUEUE_NAME_HEADER]?.toString()
                    ?: return message
                val withoutSuffix = queueName.removeSuffix(".fifo")
                val service = baseNameToService.firstOrNull { withoutSuffix.endsWith(it.first) }?.second
                if (service != null) MalaiSourceContext.value = MalaiSource(service)
                return message
            }

            override fun afterProcessing(message: Message<Any>, t: Throwable?) {
                MalaiSourceContext.unset()
            }
        }
    }
}
