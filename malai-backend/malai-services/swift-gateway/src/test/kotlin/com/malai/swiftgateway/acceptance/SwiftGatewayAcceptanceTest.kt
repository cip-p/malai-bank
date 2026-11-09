package com.malai.swiftgateway.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.payments.messages.SwiftOutboundSubmit
import com.malai.swiftgateway.support.CapturedSwiftEvents
import com.malai.swiftgateway.support.runSwiftGatewayService
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
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.awspring.cloud.sqs.operations.SqsTemplate
import java.util.UUID

abstract class SwiftGatewayAcceptanceTest : MalaiAcceptanceTest() {

    fun theSwiftSchemeWillAcceptSubmissions() = CallableAction {
        wiremock().stubFor(
            post(urlEqualTo("$SWIFT_SCHEME_PATH/swift/payments"))
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

    fun paymentsServiceSubmits(submit: SwiftOutboundSubmit) = CallableAction {
        val testId = TestIdContext.value.value
        val queueName = QueueNaming.physicalName(sqsProps().queueNamePrefix, PaymentsQueues.SWIFT_COMMAND_SUBMIT)
        sqsTemplate().send<SwiftOutboundSubmit> { options ->
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
            PaymentsQueues.SWIFT_COMMAND_SUBMIT,
            testingObjectMapper.writeValueAsString(submit),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to submit.paymentId.toString(),
                "MessageDeduplicationId" to submit.paymentId.toString(),
            ),
        )
        eventually {
            val received = wiremock().allServeEvents.any {
                it.request.url == "$SWIFT_SCHEME_PATH/swift/payments" &&
                    it.request.header(TestId.HEADER).containsValue(testId)
            }
            check(received) { "swift scheme has not yet received the submit for testId=$testId" }
        }
    }

    fun theSwiftSchemeFiresSettlementCallback(paymentId: UUID, endToEndId: UUID): Action<ActionContext> =
        serviceReceives(
            post()
                .url("/swift/callbacks/settlement")
                .header("Content-Type", "application/json")
                .body("""{"endToEndId":"$endToEndId","paymentId":"$paymentId"}"""),
        )

    fun theProducedSettledEvents() = StateCollector { _ ->
        val testId = TestIdContext.value.value
        eventually {
            check(capturedSwiftEvents().settledFor(testId).isNotEmpty()) {
                "no settled event produced for testId=$testId"
            }
        }
        capturedSwiftEvents().settledFor(testId)
    }

    fun swiftGatewayMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    private fun sqsTemplate(): SqsTemplate = getBean(SqsTemplate::class.java)
    private fun sqsProps(): MalaiSqsMessagingProperties = getBean(MalaiSqsMessagingProperties::class.java)
    private fun capturedSwiftEvents(): CapturedSwiftEvents = getBean(CapturedSwiftEvents::class.java)

    companion object {
        const val SWIFT_SCHEME_PATH = "/swift-scheme"
        private const val ACK_PLACEHOLDER_UUID = "00000000-0000-4000-9000-000000000acc"

        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "SWIFT Gateway Acceptance Tests",
                fromSystem = MalaiParty.SwiftGateway,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(SWIFT_SCHEME_PATH) -> MalaiParty.Swift
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runSwiftGatewayService(swiftSchemeBaseUrl = wiremock.url(SWIFT_SCHEME_PATH))
            }
        }
    }
}
