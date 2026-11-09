package com.malai.client.malai.payments

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.payments.builders.response.InitiatePaymentResponseBuilder.Companion.anInitiatePaymentResponse
import com.malai.client.malai.payments.builders.response.InternalPaymentResponseBuilder.Companion.anInternalPaymentResponse
import com.malai.client.malai.payments.builders.response.SettledPaymentsResponseBuilder.Companion.aSettledPaymentsResponse
import com.malai.client.malai.payments.builders.response.SettledPaymentsResponseBuilder.SettledPaymentBuilder.Companion.aSettledPayment
import com.malai.client.malai.payments.model.InitiatePaymentRequest
import com.malai.client.malai.payments.model.InitiatePaymentResponse
import com.malai.client.malai.payments.model.InternalPaymentResponse
import com.malai.client.malai.payments.model.SettledPayment
import com.malai.client.malai.payments.model.SettledPaymentsResponse
import com.malai.payments.messages.ReturnReason
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import java.time.Instant

@MalaiClientSpringTest
class MalaiPaymentsClientTest : MalaiClientTest(), WithPaymentsApi {

    @Autowired
    private lateinit var paymentsClient: MalaiPaymentsClient

    private var lastResponse: InitiatePaymentResponse? = null
    private var lastInternalPaymentResponse: InternalPaymentResponse? = null
    private var lastSettledPaymentsResponse: SettledPaymentsResponse? = null
    private var lastThrowable: Throwable? = null

    private val windowStart = Instant.parse("2026-07-19T23:00:00Z")
    private val windowEnd = Instant.parse("2026-07-20T23:00:00Z")

    private val someScreeningDecisionId = java.util.UUID.fromString("00000000-0000-4000-9000-00000000d1d1")

    @Test
    fun initiatePaymentForwardsRequestAndDeserialisesResponse() {
        given(initiatePaymentReturns(
            anInitiatePaymentResponse()
                .withPaymentId(somePaymentUid())
                .withState("SETTLED")
                .withRail("BOOK_TRANSFER")
                .withDirection("OUTBOUND")
                .withCreatedAt(Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(anotherPaymentUid())
                .withFromAccountId(someAccountUid())
                .withToAccountId(anotherAccountUid())
                .withToSortCode("65-82-37")
                .withToAccountNumber("11335577")
                .withAmountMinorUnits(2_500L)
                .withCurrency("GBP")
                .withDescription("savings top-up")
        ))

        whenever(clientCallsInitiatePayment(
            accessToken = "some-access-token",
            accountHolderId = someAccountHolderUid(),
            idempotencyKey = "transfer-key-1",
            request = InitiatePaymentRequest(
                fromAccountId = someAccountUid(),
                toSortCode = "65-82-37",
                toAccountNumber = "11335577",
                amountMinorUnits = 2_500L,
                currency = "GBP",
                description = "savings top-up",
            ),
        ))

        then(theLastInitiatePaymentResponse()) {
            this.paymentId shouldBe somePaymentUid()
            this.state shouldBe "SETTLED"
            this.rail shouldBe "BOOK_TRANSFER"
            this.direction shouldBe "OUTBOUND"
            this.fromAccountId shouldBe someAccountUid()
            this.toAccountId shouldBe anotherAccountUid()
            this.amountMinorUnits shouldBe 2_500L
            this.currency shouldBe "GBP"
        }

        and(madeTheFollowingHttpRequest {
            url == "/payments-service/api/v1/payments"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-access-token"
            headers["MAL-Account-Holder-Id"] shouldBe someAccountHolderUid().toString()
            headers["Idempotency-Key"] shouldBe "transfer-key-1"
            headers["Content-Type"] shouldBe "application/json"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "fromAccountId" To someAccountUid().toString()
                "toSortCode" To "65-82-37"
                "toAccountNumber" To "11335577"
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "savings top-up"
                "copVerdictOverride" To false
                noExtraProps()
            }
        }
    }

    @Test
    fun initiatePaymentDeserialisesReturnedPaymentWithReturnReason() {
        given(initiatePaymentReturns(
            anInitiatePaymentResponse()
                .withPaymentId(somePaymentUid())
                .withState("RETURNED")
                .withRail("FPS")
                .withDirection("OUTBOUND")
                .withCreatedAt(Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(anotherPaymentUid())
                .withFromAccountId(someAccountUid())
                .withAmountMinorUnits(5_000L)
                .withCurrency("GBP")
                .withReturnReason(ReturnReason.ACCOUNT_CLOSED)
        ))

        whenever(clientCallsInitiatePayment(
            accessToken = "some-access-token",
            accountHolderId = someAccountHolderUid(),
            idempotencyKey = "key-2",
            request = InitiatePaymentRequest(
                fromAccountId = someAccountUid(),
                toSortCode = "65-82-99",
                toAccountNumber = "99000001",
                amountMinorUnits = 5_000L,
                currency = "GBP",
            ),
        ))

        then(theLastInitiatePaymentResponse()) {
            this.state shouldBe "RETURNED"
            this.returnReason shouldBe ReturnReason.ACCOUNT_CLOSED
        }
    }

    @Test
    fun initiatePaymentDeserialisesFxConversionForSwiftRail() {
        given(initiatePaymentReturns(
            anInitiatePaymentResponse()
                .withPaymentId(somePaymentUid())
                .withState("PENDING")
                .withRail("SWIFT")
                .withDirection("OUTBOUND")
                .withCreatedAt(Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(anotherPaymentUid())
                .withFromAccountId(someAccountUid())
                .withToIban("DE89370400440532013000")
                .withToBic("DEUTDEFF")
                .withPaymentPurpose("invoice")
                .withAmountMinorUnits(10_000L)
                .withCurrency("EUR")
                .withFxConversion(11_500L, "EUR", "1.15")
        ))

        whenever(clientCallsInitiatePayment(
            accessToken = "some-access-token",
            accountHolderId = someAccountHolderUid(),
            idempotencyKey = "key-3",
            request = InitiatePaymentRequest(
                fromAccountId = someAccountUid(),
                toIban = "DE89370400440532013000",
                toBic = "DEUTDEFF",
                paymentPurpose = "invoice",
                amountMinorUnits = 10_000L,
                currency = "EUR",
            ),
        ))

        then(theLastInitiatePaymentResponse()) {
            this.rail shouldBe "SWIFT"
            this.fxConversion?.amountMinorUnits shouldBe 11_500L
            this.fxConversion?.currency shouldBe "EUR"
            this.fxConversion?.rateUsed shouldBe "1.15"
        }
    }

    private fun clientCallsInitiatePayment(
        accessToken: String,
        accountHolderId: java.util.UUID,
        idempotencyKey: String,
        request: InitiatePaymentRequest,
    ) = Action<ActionContext> {
        lastResponse = paymentsClient.initiatePayment(accessToken, accountHolderId, idempotencyKey, request)
    }

    private fun theLastInitiatePaymentResponse() = StateCollector { _ ->
        lastResponse ?: error("No initiate-payment response captured")
    }

    @Test
    fun getPaymentReadsThePaymentsOwnFacts() {
        given(getPaymentReturns(somePaymentUid(), anInternalPaymentResponse()
            .withPaymentId(somePaymentUid())
            .withState("SETTLED")
            .withRail("FPS")
            .withDirection("OUTBOUND")
            .withAmountMinorUnits(2_500L)
            .withCurrency("GBP")
            .withEndToEndId(anotherPaymentUid())
            .withCreatedAt("2026-10-15T10:30:00Z")
            .withCopVerdictOverride(false)
            .withDescription("rent")
            .withPaymentPurpose("RENT")
            .withScreeningDecisionId(someScreeningDecisionId)
            .withCreatedByAccountHolderId(someAccountHolderUid())
            .withTraceId("some-trace-id")
            .withDebtorAccountId(someAccountUid())
            .withDebtorSortCode("040004")
            .withDebtorAccountNo("12345678")
            .withDebtorName("Sarah Chen")
            .withBeneficiaryAccountId(anotherAccountUid())
            .withBeneficiarySortCode("040004")
            .withBeneficiaryAccountNo("87651236")
            .withBeneficiaryName("Rent Collector")))

        whenever(clientCallsGetPayment("some-service-token", somePaymentUid()))

        then(theLastInternalPaymentResponse()) {
            this shouldBe InternalPaymentResponse(
                paymentId = somePaymentUid(),
                state = "SETTLED",
                rail = "FPS",
                direction = "OUTBOUND",
                amountMinorUnits = 2_500L,
                currency = "GBP",
                endToEndId = anotherPaymentUid(),
                createdAt = "2026-10-15T10:30:00Z",
                copVerdictOverride = false,
                description = "rent",
                paymentPurpose = "RENT",
                screeningDecisionId = someScreeningDecisionId,
                createdByAccountHolderId = someAccountHolderUid(),
                traceId = "some-trace-id",
                debtorAccountId = someAccountUid(),
                debtorSortCode = "040004",
                debtorAccountNo = "12345678",
                debtorName = "Sarah Chen",
                beneficiaryAccountId = anotherAccountUid(),
                beneficiarySortCode = "040004",
                beneficiaryAccountNo = "87651236",
                beneficiaryName = "Rent Collector",
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/payments-service/api/v1/internal/payments/${somePaymentUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
        }
    }

    @Test
    fun anUnknownPaymentSurfacesAsAClientResponseException() {
        given(getPaymentReturnsNotFound(somePaymentUid()))

        whenever(clientAttemptsToGetPayment("some-service-token", somePaymentUid()))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    @Test
    fun readsTheSettledPaymentsForAWindow() {
        given(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            aSettledPayment()
                .withPaymentId(somePaymentUid())
                .withState("SETTLED")
                .withRail("SWIFT")
                .withDirection("OUTBOUND")
                .withAmountMinorUnits(750_000L)
                .withCurrency("GBP")
                .withCreatedAt("2026-07-20T09:00:00Z")
                .withEndToEndId(anotherPaymentUid())
                .withDebtorAccountId(someAccountUid())
                .withDebtorSortCode("040004")
                .withDebtorAccountNo("12345678")
                .withDebtorName("Sarah Chen")
                .withBeneficiaryIban("AE070331234567890123456")
                .withBeneficiaryBic("BOMLAEAD")
                .withBeneficiaryName("Layla Haddad"))))

        whenever(clientCallsSettledPayments("some-service-token", windowStart, windowEnd))

        then(theLastSettledPaymentsResponse()) {
            this shouldBe SettledPaymentsResponse(payments = listOf(
                SettledPayment(
                    paymentId = somePaymentUid(),
                    state = "SETTLED",
                    rail = "SWIFT",
                    direction = "OUTBOUND",
                    amountMinorUnits = 750_000L,
                    currency = "GBP",
                    createdAt = "2026-07-20T09:00:00Z",
                    endToEndId = anotherPaymentUid(),
                    debtorAccountId = someAccountUid(),
                    debtorSortCode = "040004",
                    debtorAccountNo = "12345678",
                    debtorName = "Sarah Chen",
                    beneficiaryIban = "AE070331234567890123456",
                    beneficiaryBic = "BOMLAEAD",
                    beneficiaryName = "Layla Haddad",
                ),
            ))
        }
        and(madeTheFollowingHttpRequest {
            url.startsWith("/payments-service/api/v1/internal/payments/settled")
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            url shouldBe "/payments-service/api/v1/internal/payments/settled" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun aQuietWindowDeserialisesAsAnEmptyListOfPayments() {
        given(settledPaymentsReturns(aSettledPaymentsResponse()))

        whenever(clientCallsSettledPayments("some-service-token", windowStart, windowEnd))

        then(theLastSettledPaymentsResponse()) {
            this shouldBe SettledPaymentsResponse(payments = emptyList())
        }
    }

    @Test
    fun surfacesASettledWindowReadFailureAsAClientResponseException() {
        given(settledPaymentsFailsWithServerError())

        whenever(clientAttemptsToReadSettledPayments("some-service-token", windowStart, windowEnd))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    private fun clientCallsSettledPayments(serviceToken: String, from: Instant, until: Instant) =
        Action<ActionContext> {
            lastSettledPaymentsResponse = paymentsClient.settledPayments(serviceToken, from, until)
        }

    private fun clientAttemptsToReadSettledPayments(serviceToken: String, from: Instant, until: Instant) =
        Action<ActionContext> {
            lastThrowable = runCatching { paymentsClient.settledPayments(serviceToken, from, until) }.exceptionOrNull()
        }

    private fun theLastSettledPaymentsResponse() = StateCollector { _ ->
        lastSettledPaymentsResponse ?: error("No settled-payments response captured")
    }

    private fun clientCallsGetPayment(serviceToken: String, paymentId: java.util.UUID) =
        Action<ActionContext> {
            lastInternalPaymentResponse = paymentsClient.getPayment(serviceToken, paymentId)
        }

    private fun clientAttemptsToGetPayment(serviceToken: String, paymentId: java.util.UUID) =
        Action<ActionContext> {
            lastThrowable = runCatching { paymentsClient.getPayment(serviceToken, paymentId) }.exceptionOrNull()
        }

    private fun theLastInternalPaymentResponse() = StateCollector { _ ->
        lastInternalPaymentResponse ?: error("No internal-payment response captured")
    }

    private fun theLastThrowable() = StateCollector { _ ->
        lastThrowable ?: error("No throwable captured")
    }
}
