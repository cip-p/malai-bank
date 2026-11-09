package com.malai.client.malai.stub

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.stub.card.builders.response.CardMerchantResponseBuilder.Companion.aCardMerchantResponse
import com.malai.client.malai.stub.card.builders.response.CardMerchantsResponseBuilder.Companion.aCardMerchantsResponse
import com.malai.client.malai.stub.card.builders.response.CardPurchaseResultBuilder.Companion.aCardPurchaseResult
import com.malai.client.malai.stub.card.model.CardAuthorisationOutcome.APPROVED
import com.malai.client.malai.stub.card.model.CardAuthorisationOutcome.DECLINED
import com.malai.client.malai.stub.card.model.CardMerchantResponse
import com.malai.client.malai.stub.card.model.CardMerchantsResponse
import com.malai.client.malai.stub.card.model.CardPurchaseForwardPayload
import com.malai.client.malai.stub.card.model.CardPurchaseResult
import com.malai.client.malai.stub.card.model.CardRefundForwardPayload
import com.malai.client.malai.stub.card.model.PresentNowResult
import com.malai.client.malai.stub.collection.model.CollectionForwardPayload
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.client.malai.stub.directory.builders.response.CounterpartiesResponseBuilder.Companion.aCounterpartiesResponse
import com.malai.client.malai.stub.directory.builders.response.CounterpartyResponseBuilder.Companion.aCounterpartyResponse
import com.malai.client.malai.stub.directory.model.CounterpartiesResponse
import com.malai.client.malai.stub.directory.model.OutboundOutcome
import com.malai.client.malai.stub.inbound.model.InboundPaymentForwardPayload
import com.malai.client.malai.stub.inbound.model.SandboxRail
import com.malai.payments.messages.ReturnReason
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import java.util.UUID

@MalaiClientSpringTest
class MalaiStubClientTest : MalaiClientTest(), WithMalaiStub {

    @Autowired
    private lateinit var client: MalaiStubClient

    private var lastResponse: CounterpartiesResponse? = null
    private var lastMerchantsResponse: CardMerchantsResponse? = null
    private var lastPurchaseResult: CardPurchaseResult? = null
    private var lastPresentResult: PresentNowResult? = null

    @Test
    fun getsCardMerchants() {
        given(getCardMerchantsReturns(aCardMerchantsResponse().withMerchants(
            aCardMerchantResponse()
                .withMerchantId("fictional-coffee")
                .withName("Fictional Coffee Co")
                .withMcc("5814"),
            aCardMerchantResponse()
                .withMerchantId("nimbus-groceries")
                .withName("Nimbus Groceries")
                .withMcc("5411"),
        )))

        whenever(clientCallsGetCardMerchants())

        then(theLastMerchantsResponse()) {
            merchants shouldBe listOf(
                CardMerchantResponse("fictional-coffee", "Fictional Coffee Co", "5814"),
                CardMerchantResponse("nimbus-groceries", "Nimbus Groceries", "5411"),
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/cards/merchants"
        }) {
            method shouldBe GET
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    @Test
    fun triggersACardPurchaseAndRelaysTheApproval() {
        given(triggerCardPurchaseReturns(aCardPurchaseResult()
            .withSchemeReference("00000000-0000-4000-8000-0000000000ab")
            .withOutcome("APPROVED")))

        whenever(clientCallsTriggerCardPurchase(CardPurchaseForwardPayload(
            pan = "9904320000012341",
            merchantId = "fictional-coffee",
            amountMinorUnits = 4370,
            currency = "GBP",
        )))

        then(theLastPurchaseResult()) {
            this shouldBe CardPurchaseResult(
                UUID.fromString("00000000-0000-4000-8000-0000000000ab"), APPROVED, null,
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/cards/purchase"
        }) {
            method shouldBe POST
            headers["Content-Type"] shouldBe "application/json"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "pan" To "9904320000012341"
                "merchantId" To "fictional-coffee"
                "amountMinorUnits" To 4370
                "currency" To "GBP"
                noExtraProps()
            }
        }
    }

    @Test
    fun triggersACardPurchaseAndRelaysADeclineWithItsReasonCode() {
        given(triggerCardPurchaseReturns(aCardPurchaseResult()
            .withSchemeReference("00000000-0000-4000-8000-0000000000ab")
            .withOutcome("DECLINED")
            .withDeclineReasonCode("62")))

        whenever(clientCallsTriggerCardPurchase(CardPurchaseForwardPayload(
            pan = "9904320000012341",
            merchantId = "nimbus-groceries",
            amountMinorUnits = 1599,
            currency = "GBP",
        )))

        then(theLastPurchaseResult()) {
            this shouldBe CardPurchaseResult(
                UUID.fromString("00000000-0000-4000-8000-0000000000ab"), DECLINED, "62",
            )
        }
    }

    @Test
    fun presentsPendingCards() {
        given(presentPendingCardsReturns(3))

        whenever(clientCallsPresentPendingCards())

        then(theLastPresentResult()) {
            this shouldBe PresentNowResult(3)
        }
        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/cards/present-now"
        }) {
            method shouldBe POST
            headers["Accept"] shouldBe "application/json"
        }
    }

    @Test
    fun triggersACardRefund() {
        given(triggerCardRefundAccepts())

        whenever(clientCallsTriggerCardRefund(CardRefundForwardPayload(
            schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000ab"),
            amountMinorUnits = 4370,
            currency = "GBP",
        )))

        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/cards/refund"
        }) {
            method shouldBe POST
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "schemeReference" To "00000000-0000-4000-8000-0000000000ab"
                "amountMinorUnits" To 4370
                "currency" To "GBP"
                noExtraProps()
            }
        }
    }

    @Test
    fun registersACardPan() {
        given(registerCardPanAccepts())

        whenever(clientCallsRegisterCardPan("9904320000012341"))

        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/cards/pans"
        }) {
            method shouldBe POST
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "pan" To "9904320000012341"
                noExtraProps()
            }
        }
    }

    @Test
    fun deserialisesEveryCounterpartyField() {
        given(getCounterpartiesReturns(
            aCounterpartiesResponse().withCounterparties(
                aCounterpartyResponse()
                    .withSortCode("60-00-01")
                    .withAccountNumber("12345678")
                    .withBank("NatWest")
                    .withNameOnRecord("Alice Walker")
                    .withOutboundOutcome("SETTLES"),
                aCounterpartyResponse()
                    .withSortCode("60-00-01")
                    .withAccountNumber("99000001")
                    .withBank("NatWest")
                    .withNameOnRecord("Jane Closed")
                    .withOutboundOutcome("RETURNS")
                    .withReturnReason("ACCOUNT_CLOSED"),
                aCounterpartyResponse()
                    .withSortCode("23-00-06")
                    .withAccountNumber("88000001")
                    .withBank("Starling")
                    .withNameOnRecord("Limbo Holdings Ltd")
                    .withOutboundOutcome("STUCK_PENDING"),
            )
        ))

        whenever(clientCallsGetCounterparties())

        then(theLastResponse()) {
            counterparties.size shouldBe 3
            counterparties[0].sortCode shouldBe "60-00-01"
            counterparties[0].accountNumber shouldBe "12345678"
            counterparties[0].bank shouldBe "NatWest"
            counterparties[0].nameOnRecord shouldBe "Alice Walker"
            counterparties[0].outboundOutcome shouldBe OutboundOutcome.SETTLES
            counterparties[0].returnReason shouldBe null

            counterparties[1].outboundOutcome shouldBe OutboundOutcome.RETURNS
            counterparties[1].returnReason shouldBe ReturnReason.ACCOUNT_CLOSED

            counterparties[2].outboundOutcome shouldBe OutboundOutcome.STUCK_PENDING
            counterparties[2].returnReason shouldBe null
        }

        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/counterparties"
        }) {
            method shouldBe GET
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    @Test
    fun postsInboundFpsWithExpectedBody() {
        given(postInboundFpsAccepts())

        whenever(clientCallsPostInbound(SandboxRail.FPS, somePayload()))

        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/inbound/fps"
        }) {
            method shouldBe POST
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body shouldBe expectedBody()
        }
    }

    @Test
    fun postsInboundBacsWithExpectedBody() {
        given(postInboundBacsAccepts())

        whenever(clientCallsPostInbound(SandboxRail.BACS, somePayload()))

        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/inbound/bacs"
        }) {
            method shouldBe POST
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body shouldBe expectedBody()
        }
    }

    @Test
    fun postsCollectionBacsWithExpectedBody() {
        given(postCollectionBacsAccepts())

        whenever(clientCallsPostCollectionBacs(someCollectionPayload()))

        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/scenarios/collections/bacs"
        }) {
            method shouldBe POST
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body shouldBe expectedCollectionBody()
        }
    }

    private fun someCollectionPayload() = CollectionForwardPayload(
        debtorSortCode = "65-82-37",
        debtorAccountNumber = "55667788",
        debtorName = "Alice Walker",
        creditorSortCode = "60-00-01",
        creditorAccountNumber = "22334455",
        creditorName = "Octopus Energy Ltd",
        amountMinorUnits = 4200,
        currency = "GBP",
        description = "Energy DD",
        endToEndId = UUID.fromString("00000000-0000-4000-8000-000000000001"),
    )

    private fun expectedCollectionBody() =
        """{"debtorSortCode":"65-82-37","debtorAccountNumber":"55667788","debtorName":"Alice Walker","creditorSortCode":"60-00-01","creditorAccountNumber":"22334455","creditorName":"Octopus Energy Ltd","amountMinorUnits":4200,"currency":"GBP","description":"Energy DD","endToEndId":"00000000-0000-4000-8000-000000000001"}"""

    private fun clientCallsPostCollectionBacs(payload: CollectionForwardPayload) =
        Action<ActionContext> {
            client.postCollectionBacs(payload)
        }

    private fun somePayload() = InboundPaymentForwardPayload(
        beneficiarySortCode = "04-00-04",
        beneficiaryAccountNumber = "12345678",
        beneficiaryName = "Main Account",
        originatorSortCode = "60-00-01",
        originatorAccountNumber = "11000001",
        originatorName = "Alice Walker",
        amountMinorUnits = 1500,
        currency = "GBP",
        description = "RENT MARCH",
    )

    private fun expectedBody() =
        """{"beneficiarySortCode":"04-00-04","beneficiaryAccountNumber":"12345678","beneficiaryName":"Main Account","originatorSortCode":"60-00-01","originatorAccountNumber":"11000001","originatorName":"Alice Walker","amountMinorUnits":1500,"currency":"GBP","description":"RENT MARCH"}"""

    private fun clientCallsGetCounterparties() = Action<ActionContext> {
        lastResponse = client.getCounterparties()
    }

    private fun clientCallsGetCardMerchants() = Action<ActionContext> {
        lastMerchantsResponse = client.getCardMerchants()
    }

    private fun clientCallsTriggerCardPurchase(payload: CardPurchaseForwardPayload) = Action<ActionContext> {
        lastPurchaseResult = client.triggerCardPurchase(payload)
    }

    private fun clientCallsPresentPendingCards() = Action<ActionContext> {
        lastPresentResult = client.presentPendingCards()
    }

    private fun clientCallsTriggerCardRefund(payload: CardRefundForwardPayload) = Action<ActionContext> {
        client.triggerCardRefund(payload)
    }

    private fun clientCallsRegisterCardPan(pan: String) = Action<ActionContext> {
        client.registerCardPan(pan)
    }

    private fun clientCallsPostInbound(rail: SandboxRail, payload: InboundPaymentForwardPayload) =
        Action<ActionContext> {
            client.postInbound(rail, payload)
        }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No counterparties response captured")
    }

    private fun theLastMerchantsResponse() = StateCollector { _ ->
        lastMerchantsResponse ?: error("No merchants response captured")
    }

    private fun theLastPurchaseResult() = StateCollector { _ ->
        lastPurchaseResult ?: error("No purchase result captured")
    }

    private fun theLastPresentResult() = StateCollector { _ ->
        lastPresentResult ?: error("No present result captured")
    }
}
