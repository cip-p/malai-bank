package com.malai.payments.acceptance.sad

import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.PostedEntryBuilder.Companion.aPostedEntry
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.support.MonitoredAccountCase
import com.malai.payments.support.WithAmlMonitoring
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import java.util.UUID
import java.util.UUID.randomUUID

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c1")
private val SOME_ALERT_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000a1")
private val AN_UNKNOWN_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000e1")

/**
 * The tipping-off invariant, as tested fact rather than intention: a customer whose account is
 * under AML monitoring must not be able to tell. Each test reads the same customer surface twice
 * over the same underlying payment — once while an OPEN case names the payer account, once with
 * monitoring lifted — and asserts the two responses are byte-identical.
 *
 * The full-payload `noExtraProps()` assertion is the other half: an AML-derived field added to
 * the customer payment view fails here before it ever reaches a customer.
 */
class TippingOffTest : PaymentsAcceptanceTest(), WithAmlMonitoring {

    @Test
    fun aMonitoredAccountsPaymentReadsIdenticallyToAnUnmonitoredOnes() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(theAccountIsNoLongerUnderAmlMonitoring())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePaymentUid(), randomUUID(), someTransferUid()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(thePayersAccountIsUnderMonitoring())
        and(theLedgerPostsTheTransfer())

        whenever(theCustomerSendsAPayment("tipping-off-monitored-key"))
        whenever(theCustomerReadsTheirPayment())

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "SETTLED"
                "rail" To "BOOK_TRANSFER"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To something
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To anotherAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To toAccountNumber()
                "amountMinorUnits" To 1_500
                "currency" To "GBP"
                noExtraProps()
            }
        }

        and(theAmlCasesForAccountInTheDatabase(someAccountUid())) {
            this shouldHaveSize 1
            this[0] shouldBe MonitoredAccountCase(SOME_CASE_ID, "OPEN")
        }

        val whatTheMonitoredCustomerSaw = theBodyOfTheLastResponse()

        and(theAccountIsNoLongerUnderAmlMonitoring())

        whenever(theCustomerReadsTheirPayment())

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe whatTheMonitoredCustomerSaw
        }
    }

    @Test
    fun anUnknownPaymentIsRefusedIdenticallyWhetherOrNotTheAccountIsMonitored() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(theAccountIsNoLongerUnderAmlMonitoring())
        and(thePayersAccountIsUnderMonitoring())

        whenever(theCustomerReadsAnUnknownPayment())

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8989"
            }
        }

        and(theAmlCasesForAccountInTheDatabase(someAccountUid())) {
            this shouldHaveSize 1
            this[0] shouldBe MonitoredAccountCase(SOME_CASE_ID, "OPEN")
        }

        val whatTheMonitoredCustomerSaw = theBodyOfTheLastResponse()

        and(theAccountIsNoLongerUnderAmlMonitoring())

        whenever(theCustomerReadsAnUnknownPayment())

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body shouldBe whatTheMonitoredCustomerSaw
        }
    }

    private fun theBodyOfTheLastResponse(): String? = httpClient.lastResponse().body

    private fun thePayersAccountIsUnderMonitoring() =
        theAccountIsUnderAmlMonitoring(someAccountUid(), SOME_CASE_ID, SOME_ALERT_DECISION_ID)

    private fun theLedgerPostsTheTransfer() = postTransactionReturns(aPostTransactionResponse()
        .withTransactionId(someTransferUid())
        .withPostedAt("2026-10-15T10:30:00Z")
        .withEntries(
            aPostedEntry().withEntryId(randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-1_500L),
            aPostedEntry().withEntryId(randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(1_500L),
        ))

    private fun theCustomerSendsAPayment(idempotencyKey: String) = paymentsServiceReceives(
        post()
            .url("/api/v1/payments")
            .header("Idempotency-Key", idempotencyKey)
            .header("Content-Type", "application/json")
            .body(anInitiatePaymentRequest()
                .withFromAccountId(someAccountUid())
                .withToSortCode(malaiSortCode)
                .withToAccountNumber(toAccountNumber())
                .withAmountMinorUnits(1_500L))
            .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
    )

    private fun theCustomerReadsTheirPayment() = paymentsServiceReceives(
        get()
            .url("/api/v1/payments/${somePaymentUid()}")
            .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
    )

    private fun theCustomerReadsAnUnknownPayment() = paymentsServiceReceives(
        get()
            .url("/api/v1/payments/$AN_UNKNOWN_PAYMENT_ID")
            .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
    )
}
