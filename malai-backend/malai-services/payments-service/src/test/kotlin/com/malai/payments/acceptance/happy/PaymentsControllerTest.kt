package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.PostedEntryBuilder.Companion.aPostedEntry
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import java.util.UUID
import java.util.UUID.randomUUID

class PaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun bookTransferPostsToLedgerAndWritesPaymentAndAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000000999"),
            someTransferUid(),
        ))
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
                .header("Idempotency-Key", "payment-book-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(2_500L)
                    .withDescription("savings top-up"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "SETTLED"
                "rail" To "BOOK_TRANSFER"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000000999"
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To anotherAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To toAccountNumber()
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "savings top-up"
                noExtraProps()
            }
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            method shouldBe POST
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "type" To "PAYMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "description" To "savings top-up"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -2_500
                        "counterpartyId" To anotherAccountUid().toString()
                        "description" To "savings top-up"
                        noExtraProps()
                    },
                    json {
                        "accountId" To anotherAccountUid().toString()
                        "amountMinorUnits" To 2_500
                        "counterpartyId" To someAccountUid().toString()
                        "description" To "savings top-up"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYMENT_INITIATED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "Payment"
            this[0]["resource_id"] shouldBe somePaymentUid().toString()
        }
    }

    @Test
    fun getPaymentReturnsThePaymentJustCreated() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000000888"),
            someTransferUid(),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")
            .withEntries(
                aPostedEntry().withEntryId(randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-1_500L),
                aPostedEntry().withEntryId(randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(1_500L),
            )))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-get-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(toAccountNumber())
                    .withAmountMinorUnits(1_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/payments/${somePaymentUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode.value() shouldBe 200
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "SETTLED"
                "rail" To "BOOK_TRANSFER"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000000888"
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To anotherAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To toAccountNumber()
                "amountMinorUnits" To 1_500
                "currency" To "GBP"
                noExtraProps()
            }
        }
    }

    @Test
    fun replayWithSameIdempotencyKeyReturnsCachedResponseWithoutSecondPaymentRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000000777"),
            someTransferUid(),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")
            .withEntries(
                aPostedEntry().withEntryId(randomUUID()).withAccountId(someAccountUid()).withAmountMinorUnits(-2_500L),
                aPostedEntry().withEntryId(randomUUID()).withAccountId(anotherAccountUid()).withAmountMinorUnits(2_500L),
            )))

        val body = anInitiatePaymentRequest()
            .withFromAccountId(someAccountUid())
            .withToSortCode(malaiSortCode)
            .withToAccountNumber(toAccountNumber())
            .withAmountMinorUnits(2_500L)
            .withDescription("replay test")

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-replay-key")
                .header("Content-Type", "application/json")
                .body(body)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "payment-replay-key")
                .header("Content-Type", "application/json")
                .body(body)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(theIdempotencyKeyResponseBody("payment-replay-key")) {
            this shouldContain """"paymentId":"${somePaymentUid()}""""
        }
    }
}
