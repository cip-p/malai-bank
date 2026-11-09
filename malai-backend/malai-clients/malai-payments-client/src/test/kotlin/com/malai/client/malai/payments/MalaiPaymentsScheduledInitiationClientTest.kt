package com.malai.client.malai.payments

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.payments.builders.response.InitiatePaymentResponseBuilder.Companion.anInitiatePaymentResponse
import com.malai.client.malai.payments.model.InitiatePaymentResponse
import com.malai.client.malai.payments.model.InternalInitiatePaymentRequest
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.POST
import java.time.Instant
import java.util.UUID

@MalaiClientSpringTest
class MalaiPaymentsScheduledInitiationClientTest : MalaiClientTest(), WithPaymentsApi {

    @Autowired
    private lateinit var paymentsClient: MalaiPaymentsClient

    private val endToEndId: UUID = UUID.fromString("00000000-0000-4000-9000-0000000e2e01")
    private var lastResponse: InitiatePaymentResponse? = null
    private var lastThrowable: Throwable? = null

    @Test
    fun scheduledInitiationForwardsRequestWithServiceTokenAndDeserialisesResponse() {
        given(initiateScheduledPaymentReturns(
            anInitiatePaymentResponse()
                .withPaymentId(somePaymentUid())
                .withState("PENDING")
                .withRail("FPS")
                .withDirection("OUTBOUND")
                .withCreatedAt(Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(endToEndId)
                .withFromAccountId(someAccountUid())
                .withToSortCode("65-82-37")
                .withToAccountNumber("11335577")
                .withToName("Rent Collector")
                .withAmountMinorUnits(2_500L)
                .withCurrency("GBP")
                .withDescription("rent")
        ))

        whenever(clientCallsInitiateScheduledPayment(
            serviceToken = "some-service-token",
            request = InternalInitiatePaymentRequest(
                endToEndId = endToEndId,
                fromAccountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                toSortCode = "65-82-37",
                toAccountNumber = "11335577",
                toName = "Rent Collector",
                amountMinorUnits = 2_500L,
                currency = "GBP",
                reference = "rent",
            ),
        ))

        then(theLastResponse()) {
            this.paymentId shouldBe somePaymentUid()
            this.state shouldBe "PENDING"
            this.rail shouldBe "FPS"
            this.direction shouldBe "OUTBOUND"
            this.endToEndId shouldBe endToEndId
            this.fromAccountId shouldBe someAccountUid()
            this.amountMinorUnits shouldBe 2_500L
            this.currency shouldBe "GBP"
        }

        and(madeTheFollowingHttpRequest {
            url == "/payments-service/api/v1/internal/payments"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["Accept"] shouldBe "application/json"
            body containsProp json {
                "endToEndId" To endToEndId.toString()
                "fromAccountId" To someAccountUid().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "toSortCode" To "65-82-37"
                "toAccountNumber" To "11335577"
                "toName" To "Rent Collector"
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "reference" To "rent"
                noExtraProps()
            }
        }
    }

    @Test
    fun scheduledInitiationRejectedForInsufficientFundsThrowsDistinctException() {
        given(initiateScheduledPaymentReturnsErrorStatus(400, """{"message":"7983"}"""))

        whenever(clientCallsInitiateScheduledPaymentCapturingError(
            serviceToken = "some-service-token",
            request = InternalInitiatePaymentRequest(
                endToEndId = endToEndId,
                fromAccountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                toSortCode = "65-82-37",
                toAccountNumber = "11335577",
                amountMinorUnits = 9_999_999L,
                currency = "GBP",
                reference = "rent",
            ),
        ))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<PaymentInsufficientFundsException>()
        }
    }

    private fun clientCallsInitiateScheduledPayment(
        serviceToken: String,
        request: InternalInitiatePaymentRequest,
    ) = Action<ActionContext> {
        lastResponse = paymentsClient.initiateScheduledPayment(serviceToken, request)
    }

    private fun clientCallsInitiateScheduledPaymentCapturingError(
        serviceToken: String,
        request: InternalInitiatePaymentRequest,
    ) = Action<ActionContext> {
        lastThrowable = runCatching { paymentsClient.initiateScheduledPayment(serviceToken, request) }.exceptionOrNull()
    }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No scheduled-initiation response captured")
    }

    private fun theLastThrowable() = StateCollector { _ ->
        lastThrowable ?: error("No throwable captured")
    }
}
