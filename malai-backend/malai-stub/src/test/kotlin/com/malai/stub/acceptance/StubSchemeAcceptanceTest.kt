package com.malai.stub.acceptance

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.stub.builders.request.CollectionRequestBuilder.Companion.aCollectionRequest
import com.malai.stub.builders.request.FpsSchemeReturnToSenderBuilder.Companion.anFpsSchemeReturnToSender
import com.malai.stub.builders.request.FpsSchemeSubmitRequestBuilder.Companion.anFpsSchemeSubmitRequest
import com.malai.stub.builders.request.InboundRequestBuilder.Companion.anInboundRequest
import com.malai.stub.builders.request.SwiftSchemeSubmitRequestBuilder.Companion.aSwiftSchemeSubmitRequest
import com.malai.stub.scheme.StubFpsScheme
import com.malai.test.acceptance.eventually
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.NO_CONTENT
import java.util.UUID

class StubSchemeAcceptanceTest : MalaiStubAcceptanceTest() {

    private val knownSortCode = "60-00-01"
    private val knownAccountNumber = "12345678"

    private val knownReturnsClosedSortCode = "60-00-01"
    private val knownReturnsClosedAccountNumber = "99000001"

    private val knownStuckSortCode = "23-00-06"
    private val knownStuckAccountNumber = "88000001"

    @Test
    fun postFpsPaymentsForKnownBeneficiaryAcksAndFiresASettlementCallbackToTheFpsGateway() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theFpsGatewayWillAcceptSettlementCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/fps/payments")
                .header("Content-Type", "application/json")
                .body(anFpsSchemeSubmitRequest()
                    .withPaymentId(paymentId)
                    .withEndToEndId(endToEndId)
                    .withCreditorSortCode(knownSortCode)
                    .withCreditorAccountNumber(knownAccountNumber))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$FPS_GATEWAY_PATH/fps/callbacks/settlement"
        }) {
            method shouldBe POST
            body containsProp json {
                "paymentId" To paymentId.toString()
                "endToEndId" To endToEndId.toString()
                noExtraProps()
            }
        }
    }

    @Test
    fun postFpsPaymentsForUnknownBeneficiaryAcksAndFiresASettlementCallbackToTheFpsGateway() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theFpsGatewayWillAcceptSettlementCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/fps/payments")
                .header("Content-Type", "application/json")
                .body(anFpsSchemeSubmitRequest()
                    .withPaymentId(paymentId)
                    .withEndToEndId(endToEndId)
                    .withCreditorSortCode(knownSortCode)
                    .withCreditorAccountNumber("00000000"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$FPS_GATEWAY_PATH/fps/callbacks/settlement"
        }) {
            method shouldBe POST
            body containsProp json {
                "paymentId" To paymentId.toString()
                "endToEndId" To endToEndId.toString()
                noExtraProps()
            }
        }
    }

    @Test
    fun postFpsPaymentsForReturnsCounterpartyAcksAndFiresAReturnCallbackWithTheConfiguredReasonCode() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theFpsGatewayWillAcceptReturnCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/fps/payments")
                .header("Content-Type", "application/json")
                .body(anFpsSchemeSubmitRequest()
                    .withPaymentId(paymentId)
                    .withEndToEndId(endToEndId)
                    .withCreditorSortCode(knownReturnsClosedSortCode)
                    .withCreditorAccountNumber(knownReturnsClosedAccountNumber))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$FPS_GATEWAY_PATH/fps/callbacks/return"
        }) {
            method shouldBe POST
            body containsProp json {
                "paymentId" To paymentId.toString()
                "endToEndId" To endToEndId.toString()
                "reasonCode" To "AC04"
                noExtraProps()
            }
        }
    }

    @Test
    fun postFpsPaymentsForAStuckPendingCounterpartyAcksWithoutFiringAnyFollowupCallback() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        whenever(malaiStubReceives(
            post()
                .url("/fps/payments")
                .header("Content-Type", "application/json")
                .body(anFpsSchemeSubmitRequest()
                    .withPaymentId(paymentId)
                    .withEndToEndId(endToEndId)
                    .withCreditorSortCode(knownStuckSortCode)
                    .withCreditorAccountNumber(knownStuckAccountNumber))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(noCallbacksWereFiredToAnyGateway())
    }

    @Test
    fun postFpsPaymentsWithCopVerdictOverrideFiresAReturnCallbackWithTheNameMismatchReasonCode() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theFpsGatewayWillAcceptReturnCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/fps/payments")
                .header("Content-Type", "application/json")
                .body(anFpsSchemeSubmitRequest()
                    .withPaymentId(paymentId)
                    .withEndToEndId(endToEndId)
                    .withCreditorSortCode(knownSortCode)
                    .withCreditorAccountNumber(knownAccountNumber)
                    .withCopVerdictOverride(true))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$FPS_GATEWAY_PATH/fps/callbacks/return"
        }) {
            body containsProp json {
                "paymentId" To paymentId.toString()
                "endToEndId" To endToEndId.toString()
                "reasonCode" To "AC03"
                noExtraProps()
            }
        }
    }

    @Test
    fun postSwiftPaymentsAcksAndFiresASettlementCallbackToTheSwiftGateway() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theSwiftGatewayWillAcceptSettlementCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/swift/payments")
                .header("Content-Type", "application/json")
                .body(aSwiftSchemeSubmitRequest()
                    .withPaymentId(paymentId)
                    .withEndToEndId(endToEndId))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(theSwiftGatewayEventuallyReceivedTheSettlementCallback(paymentId, endToEndId))
    }

    @Test
    fun postDemoInboundFpsForwardsTheCanonicalInboundNotificationToTheFpsGateway() = malaiAcceptanceTest {
        val marker = UUID.randomUUID().toString()

        given(theFpsGatewayWillAcceptInboundCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/inbound/fps")
                .header("Content-Type", "application/json")
                .body(anInboundRequest()
                    .withDescription(marker))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$FPS_GATEWAY_PATH/fps/callbacks/inbound"
        }) {
            body containsProp json {
                "creditor" To json {
                    "sortCode" To "65-82-37"
                    "accountNumber" To "10000001"
                    "name" To "Alice Walker"
                    noExtraProps()
                }
                "instructedAmount" To json {
                    "minorUnits" To 5000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "remittanceInformation" To marker
            }
        }
    }

    @Test
    fun postDemoInboundBacsForwardsTheCanonicalDirectCreditToTheBacsGateway() = malaiAcceptanceTest {
        val marker = UUID.randomUUID().toString()

        given(theBacsGatewayWillAcceptInboundCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/inbound/bacs")
                .header("Content-Type", "application/json")
                .body(anInboundRequest()
                    .withDescription(marker))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$BACS_GATEWAY_PATH/bacs/callbacks/inbound"
        }) {
            body containsProp json {
                "creditor" To json {
                    "sortCode" To "65-82-37"
                    "accountNumber" To "10000001"
                    "name" To "Alice Walker"
                    noExtraProps()
                }
                "instructedAmount" To json {
                    "minorUnits" To 5000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "remittanceInformation" To marker
            }
        }
    }

    @Test
    fun postFpsReturnsRecordsTheReturnToSenderInTheStubFpsScheme() = malaiAcceptanceTest {
        val endToEndId = UUID.randomUUID()

        whenever(malaiStubReceives(
            post()
                .url("/fps/returns")
                .header("Content-Type", "application/json")
                .body(anFpsSchemeReturnToSender()
                    .withEndToEndId(endToEndId)
                    .withCreditorSortCode(knownSortCode)
                    .withCreditorAccountNumber(knownAccountNumber)
                    .withReasonCode("AC01"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(theStubFpsSchemeRecordedAReturnFor(endToEndId))
    }

    private fun noCallbacksWereFiredToAnyGateway() = CallableAction {
        val currentTestId = TestIdContext.value.value
        val urlsHit = wiremock().allServeEvents
            .filter { it.request.headers.getHeader(TestId.HEADER).containsValue(currentTestId) }
            .map { it.request.url }
        check(urlsHit.isEmpty()) {
            "expected no gateway callbacks but the stub fired: $urlsHit"
        }
    }

    private fun theSwiftGatewayEventuallyReceivedTheSettlementCallback(
        paymentId: UUID,
        endToEndId: UUID,
    ) = CallableAction {
        val testId = TestIdContext.value.value
        eventually {
            val received = wiremock().allServeEvents.any { event ->
                event.request.url == "$SWIFT_GATEWAY_PATH/swift/callbacks/settlement" &&
                    event.request.headers.getHeader(TestId.HEADER).containsValue(testId) &&
                    event.request.bodyAsString.contains(paymentId.toString()) &&
                    event.request.bodyAsString.contains(endToEndId.toString())
            }
            check(received) { "swift settlement callback not yet received for testId=$testId" }
        }
    }

    private fun theStubFpsSchemeRecordedAReturnFor(endToEndId: UUID) = CallableAction {
        check(getBean(StubFpsScheme::class.java).hasReceivedReturnFor(endToEndId)) {
            "expected StubFpsScheme to have recorded a return for endToEndId=$endToEndId"
        }
    }

    @Test
    fun postScenarioCollectionBacsForwardsTheCanonicalCollectionToTheBacsGateway() = malaiAcceptanceTest {
        val marker = UUID.randomUUID().toString()
        val endToEndId = UUID.randomUUID()

        given(theBacsGatewayWillAcceptCollectionCallbacks())

        whenever(malaiStubReceives(
            post()
                .url("/scenarios/collections/bacs")
                .header("Content-Type", "application/json")
                .body(aCollectionRequest()
                    .withEndToEndId(endToEndId)
                    .withDescription(marker))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(malaiStubMadeTheFollowingHttpRequest {
            url == "$BACS_GATEWAY_PATH/bacs/callbacks/collection"
        }) {
            body containsProp json {
                "endToEndId" To endToEndId.toString()
                "creditor" To json {
                    "sortCode" To "60-00-01"
                    "accountNumber" To "22334455"
                    "name" To "Octopus Energy Ltd"
                    noExtraProps()
                }
                "debtor" To json {
                    "sortCode" To "65-82-37"
                    "accountNumber" To "10000001"
                    "name" To "Alice Walker"
                    noExtraProps()
                }
                "instructedAmount" To json {
                    "minorUnits" To 4200
                    "currency" To "GBP"
                    noExtraProps()
                }
                "remittanceInformation" To marker
            }
        }
    }
}
