package com.malai.sqs

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

@Component
class QueueProvisioner(
    private val sqsAsyncClient: SqsAsyncClient,
    private val props: MalaiSqsMessagingProperties,
    private val catalogs: ObjectProvider<QueueCatalog>,
) : SmartInitializingSingleton {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterSingletonsInstantiated() {
        val definitions = catalogs.orderedStream().toList().flatMap { it.definitions() }
        if (definitions.isEmpty()) {
            log.info("skip provisioning SQS queues, no definition found")
            return
        }
        log.info("provisioning {} SQS queue(s) with prefix '{}'", definitions.size, props.queueNamePrefix)
        definitions.forEach(::provision)
    }

    private fun provision(definition: QueueDefinition) {
        val deadLetterName = QueueNaming.deadLetterName(props.queueNamePrefix, definition.baseName)
        val deadLetterArn = createFifoQueue(deadLetterName, redrivePolicy = null)
            .let(::queueArn)

        val queueName = QueueNaming.physicalName(props.queueNamePrefix, definition.baseName)
        createFifoQueue(
            queueName,
            redrivePolicy = """{"deadLetterTargetArn":"$deadLetterArn","maxReceiveCount":"$MAX_RECEIVE_COUNT"}""",
        )
    }

    private fun createFifoQueue(name: String, redrivePolicy: String?): String {
        val attributes = buildMap {
            put(QueueAttributeName.FIFO_QUEUE, "true")
            put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false")
            redrivePolicy?.let { put(QueueAttributeName.REDRIVE_POLICY, it) }
        }
        return sqsAsyncClient.createQueue { it.queueName(name).attributes(attributes) }
            .get()
            .queueUrl()
    }

    private fun queueArn(queueUrl: String): String =
        sqsAsyncClient.getQueueAttributes {
            it.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN)
        }.get().attributes()[QueueAttributeName.QUEUE_ARN]
            ?: error("could not resolve ARN for queue $queueUrl")

    companion object {
        private const val MAX_RECEIVE_COUNT = 3
    }
}
