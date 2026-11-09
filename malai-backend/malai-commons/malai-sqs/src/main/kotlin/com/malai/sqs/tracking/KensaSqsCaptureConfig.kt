package com.malai.sqs.tracking

import tools.jackson.databind.ObjectMapper
import com.malai.commons.tracking.KensaEventPublisher
import com.malai.commons.tracking.MalaiSourceQueues
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueCatalog
import com.malai.sqs.QueueNaming
import io.awspring.cloud.autoconfigure.sqs.SqsAsyncClientCustomizer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.services.sqs.SqsAsyncClient

/**
 * Wires the [KensaSqsExecutionInterceptor] into the shared [SqsAsyncClient] so every SendMessage
 * stamps `X-Malai-Source` and every Send/ReceiveMessage emits a Kensa envelope to
 * `kensa-events.fifo`. The test JVM consumes that queue to drive its Kensa renderer.
 *
 * `@Profile("e2e-tests")` only — production deployments don't carry the interceptor or the queue.
 */
@Configuration
@Profile("e2e-tests")
class KensaSqsCaptureConfig {

    @Bean
    fun kensaEventPublisher(
        applicationContext: ApplicationContext,
        objectMapper: ObjectMapper,
        sqsProps: MalaiSqsMessagingProperties,
    ): KensaEventPublisher {
        val sqsAsyncClientProvider: () -> SqsAsyncClient = {
            applicationContext.getBean(SqsAsyncClient::class.java)
        }
        val resolver = KensaQueueResolver(
            sqsAsyncClientProvider = sqsAsyncClientProvider,
            physicalQueueName = { QueueNaming.physicalName(sqsProps.queueNamePrefix, KensaEventQueue.BASE_NAME) },
        )
        return KensaSqsEventPublisher(
            sqsAsyncClientProvider = sqsAsyncClientProvider,
            kensaQueueResolver = resolver,
            objectMapper = objectMapper,
        )
    }

    @Bean
    fun kensaSqsAwsClientCustomizer(
        publisher: KensaEventPublisher,
        catalogs: ObjectProvider<QueueCatalog>,
        sourceQueues: ObjectProvider<MalaiSourceQueues>,
    ): SqsAsyncClientCustomizer {
        val matcher = QueueBaseNameMatcher(
            catalogs.orderedStream()
                .toList()
                .flatMap { it.definitions().map { d -> d.baseName } }
                .filter { it != KensaEventQueue.BASE_NAME }
        )
        val queueToConsumer: Map<String, String> = sourceQueues
            .orderedStream()
            .toList()
            .flatMap { route -> route.queueBaseNames.map { it to route.serviceName } }
            .toMap()
        val interceptor = KensaSqsExecutionInterceptor(
            publisher = publisher,
            queueBaseNameMatcher = matcher,
            queueBaseNameToConsumerService = queueToConsumer,
        )
        return SqsAsyncClientCustomizer { builder ->
            builder.overrideConfiguration(
                builder.overrideConfiguration().toBuilder()
                    .addExecutionInterceptor(interceptor)
                    .build()
            )
        }
    }
}
