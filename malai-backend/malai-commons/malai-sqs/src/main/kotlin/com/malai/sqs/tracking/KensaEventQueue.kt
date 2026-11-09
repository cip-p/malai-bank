package com.malai.sqs.tracking

import com.malai.sqs.QueueCatalog
import com.malai.sqs.QueueDefinition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

object KensaEventQueue {
    const val BASE_NAME: String = "kensa-events"
}

/**
 * Provisions `kensa-events.fifo` (the queue containers publish KensaEventEnvelope facts to and
 * the e2e test JVM consumes) only when the `e2e-tests` profile is active, so dev / prod
 * deployments don't carry the queue.
 */
@Configuration
@Profile("e2e-tests")
class KensaEventQueueCatalogConfig {

    @Bean
    fun kensaEventQueueCatalog(): QueueCatalog = object : QueueCatalog {
        override fun definitions(): List<QueueDefinition> = listOf(QueueDefinition(KensaEventQueue.BASE_NAME))
    }
}
