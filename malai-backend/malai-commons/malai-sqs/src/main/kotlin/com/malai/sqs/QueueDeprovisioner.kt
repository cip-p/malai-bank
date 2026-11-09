package com.malai.sqs

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@Component
@Profile("acceptance-tests")
class QueueDeprovisioner(
    private val sqsAsyncClient: SqsAsyncClient,
    private val props: MalaiSqsMessagingProperties,
    catalogs: ObjectProvider<QueueCatalog>,
) : DisposableBean {

    private val log = LoggerFactory.getLogger(javaClass)

    private val queueNames: List<String> = catalogs.orderedStream().toList()
        .flatMap { it.definitions() }
        .flatMap {
            listOf(
                QueueNaming.physicalName(props.queueNamePrefix, it.baseName),
                QueueNaming.deadLetterName(props.queueNamePrefix, it.baseName),
            )
        }

    override fun destroy() {
        if (props.queueNamePrefix.isEmpty()) {
            log.info("skip deprovisioning SQS queues, unprefixed queues are shared")
            return
        }
        log.info("deprovisioning {} SQS queue(s) with prefix '{}'", queueNames.size, props.queueNamePrefix)
        queueNames.forEach(::deleteQueue)
    }

    private fun deleteQueue(name: String) {
        runCatching {
            val queueUrl = sqsAsyncClient.getQueueUrl { it.queueName(name) }.get().queueUrl()
            sqsAsyncClient.deleteQueue { it.queueUrl(queueUrl) }.get()
        }.onFailure { log.warn("could not deprovision SQS queue '{}': {}", name, it.message) }
    }
}
