package com.malai.fpsgateway.acceptance

import com.malai.fpsgateway.support.runFpsGatewayService
import com.malai.sqs.QueueCatalog
import com.malai.sqs.QueueNaming
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.context.ConfigurableApplicationContext
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.util.UUID

class ProvisionedQueuesLifecycleTest : FpsGatewayAcceptanceTest() {

    private val queueNamePrefix = "t${UUID.randomUUID().toString().replace("-", "").take(8)}-"
    private lateinit var anotherFpsGateway: ConfigurableApplicationContext

    @Test
    fun theQueuesAServiceProvisionsForItselfAreRemovedWhenItShutsDown() = malaiAcceptanceTest {
        given(anotherFpsGatewayIsRunningWithQueueNamePrefix(queueNamePrefix))
        and(theBrokerHoldsEveryQueueThatGatewayProvisionedUnder(queueNamePrefix))

        whenever(thatFpsGatewayShutsDown())

        then(theQueuesOnTheBrokerUnder(queueNamePrefix)) {
            this.shouldBeEmpty()
        }
    }

    private fun anotherFpsGatewayIsRunningWithQueueNamePrefix(prefix: String) = CallableAction {
        anotherFpsGateway = runFpsGatewayService(
            fpsSchemeBaseUrl = wiremock().url(FPS_SCHEME_PATH),
            queueNamePrefix = prefix,
        )
    }

    private fun theBrokerHoldsEveryQueueThatGatewayProvisionedUnder(prefix: String) = CallableAction {
        val expected = anotherFpsGateway.getBeansOfType(QueueCatalog::class.java).values
            .flatMap { it.definitions() }
            .flatMap {
                listOf(QueueNaming.physicalName(prefix, it.baseName), QueueNaming.deadLetterName(prefix, it.baseName))
            }
            .toSet()
        val actual = queueNamesOnTheBrokerUnder(prefix).toSet()
        check(actual == expected) { "expected the broker to hold $expected under '$prefix' but it holds $actual" }
    }

    private fun thatFpsGatewayShutsDown() = Action<ActionContext> {
        anotherFpsGateway.close()
    }

    private fun theQueuesOnTheBrokerUnder(prefix: String) = StateCollector { _ ->
        queueNamesOnTheBrokerUnder(prefix)
    }

    private fun queueNamesOnTheBrokerUnder(prefix: String): List<String> =
        getBean(SqsAsyncClient::class.java)
            .listQueues { it.queueNamePrefix(prefix) }
            .get()
            .queueUrls()
            .map { it.substringAfterLast('/') }
}
