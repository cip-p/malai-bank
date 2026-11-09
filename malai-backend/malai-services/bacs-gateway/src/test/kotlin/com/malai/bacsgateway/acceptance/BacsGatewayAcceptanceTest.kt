package com.malai.bacsgateway.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.bacsgateway.support.CapturedBacsEvents
import com.malai.bacsgateway.support.runBacsGatewayService
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.payments.messages.BacsCollectionReturn
import com.malai.scheme.contracts.bacs.BacsSchemeDirectCredit
import com.malai.scheme.contracts.bacs.BacsSchemeDirectDebitCollection
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.payments.PaymentsQueues
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.eventually
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.awspring.cloud.sqs.operations.SqsTemplate

abstract class BacsGatewayAcceptanceTest : MalaiAcceptanceTest() {

    fun theBacsSchemeNotifiesInbound(credit: BacsSchemeDirectCredit): Action<ActionContext> =
        serviceReceives(
            post()
                .url("/bacs/callbacks/inbound")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(credit)),
        )

    fun theBacsSchemeOriginatesCollection(collection: BacsSchemeDirectDebitCollection): Action<ActionContext> =
        serviceReceives(
            post()
                .url("/bacs/callbacks/collection")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(collection)),
        )

    fun theProducedReceivedEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedBacsEvents().receivedFor(testId).isNotEmpty()) {
                "no received event produced for testId=$testId"
            }
        }
        capturedBacsEvents().receivedFor(testId)
    }

    fun theProducedCollectionEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedBacsEvents().collectionFor(testId).isNotEmpty()) {
                "no collection event produced for testId=$testId"
            }
        }
        capturedBacsEvents().collectionFor(testId)
    }

    fun theBacsSchemeWillAcceptReturns() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$BACS_SCHEME_PATH/bacs/collections/returns")).willReturn(aResponse().withStatus(202)),
        )
    }

    fun paymentsServiceCommandsCollectionReturn(returned: BacsCollectionReturn) = CallableAction {
        val testId = TestIdContext.value.value
        val queueName = QueueNaming.physicalName(sqsProps().queueNamePrefix, PaymentsQueues.BACS_COMMAND_RETURN)
        sqsTemplate().send<BacsCollectionReturn> { options ->
            options
                .queue(queueName)
                .payload(returned)
                .messageGroupId(returned.endToEndId.toString())
                .messageDeduplicationId(returned.endToEndId.toString())
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureCommand(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.BACS_COMMAND_RETURN,
            testingObjectMapper.writeValueAsString(returned),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to returned.endToEndId.toString(),
                "MessageDeduplicationId" to returned.endToEndId.toString(),
            ),
        )
        eventually {
            val received = wiremock().allServeEvents.any {
                it.request.url == "$BACS_SCHEME_PATH/bacs/collections/returns" &&
                    it.request.header(TestId.HEADER).containsValue(testId)
            }
            check(received) { "bacs scheme has not yet received the collection return for testId=$testId" }
        }
    }

    fun bacsGatewayMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    private fun capturedBacsEvents(): CapturedBacsEvents = getBean(CapturedBacsEvents::class.java)
    private fun sqsTemplate(): SqsTemplate = getBean(SqsTemplate::class.java)
    private fun sqsProps(): MalaiSqsMessagingProperties = getBean(MalaiSqsMessagingProperties::class.java)

    companion object {
        const val BACS_SCHEME_PATH = "/bacs-scheme"

        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "BACS Gateway Acceptance Tests",
                fromSystem = MalaiParty.BacsGateway,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(BACS_SCHEME_PATH) -> MalaiParty.Bacs
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runBacsGatewayService(bacsSchemeBaseUrl = wiremock.url(BACS_SCHEME_PATH))
            }
        }
    }
}
