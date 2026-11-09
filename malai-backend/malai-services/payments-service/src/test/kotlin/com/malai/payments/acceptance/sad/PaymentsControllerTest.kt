package com.malai.payments.acceptance.sad

import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.PostedEntryBuilder.Companion.aPostedEntry
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import java.util.UUID.randomUUID

class PaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun missingIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(ownAccountsExistInLookup())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7991"
            }
        }
    }

    @Test
    fun replayWithDifferentBodyReturns422() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePaymentUid(), randomUUID(), someTransferUid()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")
            .withEntries(
                aPostedEntry().withEntryId(randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-2_500L),
                aPostedEntry().withEntryId(randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(2_500L),
            )))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-mismatch-key")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-mismatch-key")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(9_999L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7990"
            }
        }
    }

    @Test
    fun insufficientFundsReturns400AndDoesNotWritePayment() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturnsInsufficientFunds())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-insuf-key")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7983"
            }
        }

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.none { it["action"] == "PAYMENT_INITIATED" } shouldBe true
        }
    }

    @Test
    fun paymentToSameAccountReturns400() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-self-key")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(fromAccountNumber())
                    .withAmountMinorUnits(2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7980"
            }
        }
    }

    @Test
    fun ownershipViolationOnFromAccountReturns404() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(theAccountDoesNotBelongToTheHolder(someAccountUid(), anotherAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-wrong-holder-key")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), anotherAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
            }
        }
    }

    @Test
    fun getPaymentForAnotherHoldersPaymentReturns404() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePaymentUid(), randomUUID(), someTransferUid()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")
            .withEntries(
                aPostedEntry().withEntryId(randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-1_000L),
                aPostedEntry().withEntryId(randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(1_000L),
            )))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-foreign-get-key")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(1_000L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/payments/${somePaymentUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(anotherUserId(), anotherAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8989"
            }
        }
    }

    @Test
    fun supportAgentJwtOnPaymentReturns403() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(ownAccountsExistInLookup())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-rbac-key")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(2_500L))
                .withJwtTokenForUserIdAndAccountHolderId(
                    someUserId(),
                    someAccountHolderUid(),
                    listOf("support_agent"),
                )
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }
}
