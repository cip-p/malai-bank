package com.malai.accounts.acceptance.happy

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.entity.AccountType.CREDIT_CARD
import com.malai.accounts.entity.AccountType.PRIMARY
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.accounts.support.WithTransfers
import com.malai.client.malai.payments.WithPaymentsApi
import com.malai.client.malai.payments.builders.response.InitiatePaymentResponseBuilder.Companion.anInitiatePaymentResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import java.time.Instant
import java.util.UUID.randomUUID

class TransfersControllerTest : AccountsAcceptanceTest(), WithTransfers, WithPaymentsApi {

    @Test
    fun ownToOwnTransferDelegatesToPaymentsAndMapsResponseBack() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 10_000L))
        and(initiatePaymentReturns(
            anInitiatePaymentResponse()
                .withPaymentId(someTransferUid())
                .withState("SETTLED")
                .withRail("BOOK_TRANSFER")
                .withDirection("OUTBOUND")
                .withCreatedAt(Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(randomUUID())
                .withFromAccountId(someAccountUid())
                .withToAccountId(anotherAccountUid())
                .withToSortCode(malaiSortCode)
                .withToAccountNumber(toAccountNumber())
                .withAmountMinorUnits(2_500L)
                .withCurrency("GBP")
                .withDescription("savings top-up")
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "transfer-key-1")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber(), 2_500L, "savings top-up"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To anotherAccountUid().toString()
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "postedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "${WithPaymentsApi.CONTEXT_PATH}/api/v1/payments"
        }) {
            method shouldBe POST
            headers["Idempotency-Key"] shouldBe "transfer-key-1"
            headers["Content-Type"] shouldBe "application/json"
            headers["Authorization"]!! shouldContain "Bearer "
            headers["MAL-Account-Holder-Id"] shouldBe someAccountHolderUid().toString()
            body containsProp json {
                "fromAccountId" To someAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To toAccountNumber()
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "savings top-up"
                "copVerdictOverride" To false
                noExtraProps()
            }
        }
    }

    @Test
    fun repaymentToACreditCardAccountDelegatesToPaymentsAsABookTransfer() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
            accountType = PRIMARY,
            name = "Personal",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = fromAccountNumber(),
        ))
        and(anAccountExists(
            accountId = anotherAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
            accountType = CREDIT_CARD,
            name = "Credit Card",
        ))
        and(anIdentifierExistsForAccount(
            accountId = anotherAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = toAccountNumber(),
        ))
        and(initiatePaymentReturns(
            anInitiatePaymentResponse()
                .withPaymentId(someTransferUid())
                .withState("SETTLED")
                .withRail("BOOK_TRANSFER")
                .withDirection("OUTBOUND")
                .withCreatedAt(Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(randomUUID())
                .withFromAccountId(someAccountUid())
                .withToAccountId(anotherAccountUid())
                .withToSortCode(malaiSortCode)
                .withToAccountNumber(toAccountNumber())
                .withAmountMinorUnits(30_000L)
                .withCurrency("GBP")
                .withDescription("Pay credit card")
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "repayment-key-1")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber(), 30_000L, "Pay credit card"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "transactionId" To someTransferUid().toString()
                "fromAccountId" To someAccountUid().toString()
                "toAccountId" To anotherAccountUid().toString()
                "amountMinorUnits" To 30_000
                "currency" To "GBP"
                "postedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "${WithPaymentsApi.CONTEXT_PATH}/api/v1/payments"
        }) {
            method shouldBe POST
            headers["Idempotency-Key"] shouldBe "repayment-key-1"
            headers["Authorization"]!! shouldContain "Bearer "
            body containsProp json {
                "fromAccountId" To someAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To toAccountNumber()
                "amountMinorUnits" To 30_000
                "currency" To "GBP"
                "description" To "Pay credit card"
                "copVerdictOverride" To false
                noExtraProps()
            }
        }
    }

    @Test
    fun replayWithSameIdempotencyKeyDoesNotCallPaymentsServiceAgain() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 10_000L))
        and(initiatePaymentReturns(
            anInitiatePaymentResponse()
                .withPaymentId(someTransferUid())
                .withState("SETTLED")
                .withRail("BOOK_TRANSFER")
                .withDirection("OUTBOUND")
                .withCreatedAt(Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(randomUUID())
                .withFromAccountId(someAccountUid())
                .withToAccountId(anotherAccountUid())
                .withToSortCode(malaiSortCode)
                .withToAccountNumber(toAccountNumber())
                .withAmountMinorUnits(2_500L)
                .withCurrency("GBP")
        ))

        val body = transferBody(toAccountNumber(), 2_500L, "replay test")

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "transfer-replay-key")
                .header("Content-Type", "application/json")
                .body(body)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "transfer-replay-key")
                .header("Content-Type", "application/json")
                .body(body)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "${WithPaymentsApi.CONTEXT_PATH}/api/v1/payments"
        }) {
            this shouldHaveSize 1
        }

        and(theIdempotencyKeyResponseBody("transfer-replay-key")) {
            this shouldContain """"transactionId":"${someTransferUid()}""""
        }
    }
}
