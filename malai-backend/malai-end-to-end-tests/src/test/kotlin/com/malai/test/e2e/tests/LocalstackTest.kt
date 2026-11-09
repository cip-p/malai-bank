package com.malai.test.e2e.tests

import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.MalaiE2ETestContext
import dev.kensa.StateCollector
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

class LocalstackTest : MalaiE2ETest() {

    @Test
    fun applicationsProvisionTheCatalogQueuesAndTheirDeadLetterQueues() = malaiTest {
        then(theProvisionedQueuesInLocalstack()) {
            this shouldContainExactlyInAnyOrder listOf(
                "payments-fps-command-submit.fifo",
                "payments-fps-command-submit-dlq.fifo",
                "payments-fps-command-return.fifo",
                "payments-fps-command-return-dlq.fifo",
                "payments-fps-event-settled.fifo",
                "payments-fps-event-settled-dlq.fifo",
                "payments-fps-event-returned.fifo",
                "payments-fps-event-returned-dlq.fifo",
                "payments-fps-event-received.fifo",
                "payments-fps-event-received-dlq.fifo",
                "payments-swift-command-submit.fifo",
                "payments-swift-command-submit-dlq.fifo",
                "payments-swift-event-settled.fifo",
                "payments-swift-event-settled-dlq.fifo",
                "payments-bacs-event-received.fifo",
                "payments-bacs-event-received-dlq.fifo",
                "payments-bacs-event-collection.fifo",
                "payments-bacs-event-collection-dlq.fifo",
                "payments-bacs-command-return.fifo",
                "payments-bacs-command-return-dlq.fifo",
                "cards-scheme-event-presented.fifo",
                "cards-scheme-event-presented-dlq.fifo",
                "cards-scheme-event-reversed.fifo",
                "cards-scheme-event-reversed-dlq.fifo",
                "cards-scheme-event-refunded.fifo",
                "cards-scheme-event-refunded-dlq.fifo",
                "kensa-events.fifo",
                "kensa-events-dlq.fifo",
            )
        }
    }

    private fun theProvisionedQueuesInLocalstack(): StateCollector<List<String>> =
        StateCollector { _ ->
            sqsClient().use { sqs ->
                sqs.listQueues().queueUrls().map { it.substringAfterLast("/") }
            }
        }

    private fun sqsClient(): SqsClient =
        SqsClient.builder()
            .endpointOverride(URI.create(MalaiE2ETestContext.LOCALSTACK_URL))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
            )
            .build()
}
