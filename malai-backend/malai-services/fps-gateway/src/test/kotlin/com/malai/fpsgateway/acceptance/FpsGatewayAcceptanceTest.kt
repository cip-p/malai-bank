package com.malai.fpsgateway.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.fpsgateway.support.CapturedFpsEvents
import com.malai.fpsgateway.support.runFpsGatewayService
import com.malai.payments.messages.FpsInboundReturned
import com.malai.payments.messages.FpsOutboundReturned
import com.malai.payments.messages.FpsOutboundSettled
import com.malai.payments.messages.FpsOutboundSubmit
import com.malai.scheme.contracts.fps.FpsSchemeInboundNotification
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
import com.malai.test.acceptance.kensa.interaction.PartyLookupByUrl
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.awspring.cloud.sqs.operations.SqsTemplate
import java.util.UUID

abstract class FpsGatewayAcceptanceTest : MalaiAcceptanceTest() {

    fun theFpsSchemeWillAcceptSubmissions() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$FPS_SCHEME_PATH/fps/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"endToEndId":"$ACK_PLACEHOLDER_UUID","paymentId":"$ACK_PLACEHOLDER_UUID","status":"ACCEPTED"}""",
                        ),
                ),
        )
    }

    fun paymentsServiceSubmits(submit: FpsOutboundSubmit) = CallableAction {
        val testId = TestIdContext.value.value
        val queueName = QueueNaming.physicalName(sqsProps().queueNamePrefix, PaymentsQueues.FPS_COMMAND_SUBMIT)
        sqsTemplate().send<FpsOutboundSubmit> { options ->
            options
                .queue(queueName)
                .payload(submit)
                .messageGroupId(submit.paymentId.toString())
                .messageDeduplicationId(submit.paymentId.toString())
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureCommand(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.FPS_COMMAND_SUBMIT,
            testingObjectMapper.writeValueAsString(submit),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to submit.paymentId.toString(),
                "MessageDeduplicationId" to submit.paymentId.toString(),
            ),
        )
        eventually {
            val received = wiremock().allServeEvents.any {
                it.request.url == "$FPS_SCHEME_PATH/fps/payments" &&
                    it.request.header(TestId.HEADER).containsValue(testId)
            }
            check(received) { "fps scheme has not yet received the submit for testId=$testId" }
        }
    }

    fun theFpsSchemeFiresSettlementCallback(paymentId: UUID, endToEndId: UUID): Action<ActionContext> =
        serviceReceives(
            post()
                .url("/fps/callbacks/settlement")
                .header("Content-Type", "application/json")
                .body("""{"endToEndId":"$endToEndId","paymentId":"$paymentId"}"""),
        )

    fun theFpsSchemeFiresReturnCallback(
        paymentId: UUID,
        endToEndId: UUID,
        reasonCode: String,
    ): Action<ActionContext> =
        serviceReceives(
            post()
                .url("/fps/callbacks/return")
                .header("Content-Type", "application/json")
                .body("""{"endToEndId":"$endToEndId","paymentId":"$paymentId","reasonCode":"$reasonCode"}"""),
        )

    fun theFpsSchemeNotifiesInbound(notification: FpsSchemeInboundNotification): Action<ActionContext> =
        serviceReceives(
            post()
                .url("/fps/callbacks/inbound")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(notification)),
        )

    fun theFpsSchemeWillAcceptReturns() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$FPS_SCHEME_PATH/fps/returns")).willReturn(aResponse().withStatus(202)),
        )
    }

    fun paymentsServiceCommandsReturn(returned: FpsInboundReturned) = CallableAction {
        val testId = TestIdContext.value.value
        val queueName = QueueNaming.physicalName(sqsProps().queueNamePrefix, PaymentsQueues.FPS_COMMAND_RETURN)
        sqsTemplate().send<FpsInboundReturned> { options ->
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
            PaymentsQueues.FPS_COMMAND_RETURN,
            testingObjectMapper.writeValueAsString(returned),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to returned.endToEndId.toString(),
                "MessageDeduplicationId" to returned.endToEndId.toString(),
            ),
        )
        eventually {
            val received = wiremock().allServeEvents.any {
                it.request.url == "$FPS_SCHEME_PATH/fps/returns" &&
                    it.request.header(TestId.HEADER).containsValue(testId)
            }
            check(received) { "fps scheme has not yet received the return-to-sender for testId=$testId" }
        }
    }

    fun theProducedReceivedEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedFpsEvents().receivedFor(testId).isNotEmpty()) {
                "no received event produced for testId=$testId"
            }
        }
        capturedFpsEvents().receivedFor(testId)
    }

    fun theProducedSettledEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedFpsEvents().settledFor(testId).isNotEmpty()) {
                "no settled event produced for testId=$testId"
            }
        }
        capturedFpsEvents().settledFor(testId)
    }

    fun theProducedReturnedEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedFpsEvents().returnedFor(testId).isNotEmpty()) {
                "no returned event produced for testId=$testId"
            }
        }
        capturedFpsEvents().returnedFor(testId)
    }

    fun fpsGatewayMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun fpsGatewayReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    private fun sqsTemplate(): SqsTemplate = getBean(SqsTemplate::class.java)
    private fun sqsProps(): MalaiSqsMessagingProperties = getBean(MalaiSqsMessagingProperties::class.java)
    private fun capturedFpsEvents(): CapturedFpsEvents = getBean(CapturedFpsEvents::class.java)

    companion object {
        const val FPS_SCHEME_PATH = "/fps-scheme"
        private const val ACK_PLACEHOLDER_UUID = "00000000-0000-4000-9000-000000000acc"

        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "FPS Gateway Acceptance Tests",
                fromSystem = MalaiParty.FpsGateway,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(FPS_SCHEME_PATH) -> MalaiParty.Fps
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runFpsGatewayService(fpsSchemeBaseUrl = wiremock.url(FPS_SCHEME_PATH))
            }
        }
    }
}
