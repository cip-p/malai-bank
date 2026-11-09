package com.malai.accounts.acceptance.sad

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.support.WithTransfers
import com.malai.client.malai.payments.WithPaymentsApi
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT

class TransfersControllerTest : AccountsAcceptanceTest(), WithTransfers, WithPaymentsApi {

    @Test
    fun missingIdempotencyKeyReturns400AndDoesNotCallPayments() = malaiAcceptanceTest {
        given(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 10_000L))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber(), 2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7991"
            }
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "${WithPaymentsApi.CONTEXT_PATH}/api/v1/payments"
        }) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun replayWithDifferentBodyReturns422() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 10_000L))
        and(initiatePaymentReturns(
            com.malai.client.malai.payments.builders.response.InitiatePaymentResponseBuilder
                .Companion.anInitiatePaymentResponse()
                .withPaymentId(someTransferUid())
                .withState("SETTLED")
                .withRail("BOOK_TRANSFER")
                .withDirection("OUTBOUND")
                .withCreatedAt(java.time.Instant.parse("2026-10-15T10:30:00Z"))
                .withEndToEndId(java.util.UUID.randomUUID())
                .withFromAccountId(someAccountUid())
                .withToAccountId(anotherAccountUid())
                .withAmountMinorUnits(2_500L)
                .withCurrency("GBP")
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "transfer-mismatch-key")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber(), 2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "transfer-mismatch-key")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber(), 9_999L))
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
    fun paymentsInsufficientFundsTranslatesToTransferInsufficientFunds() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 100L))
        and(initiatePaymentReturnsErrorStatus(400, """{"message":"7983"}"""))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "insufficient-funds-key")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber(), 2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7995"
            }
        }
    }

    @Test
    fun unknownDestinationLocallyReturns400WithoutCallingPayments() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 10_000L))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "unknown-dest-key")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber = "99999999", amountMinorUnits = 2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7987"
            }
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "${WithPaymentsApi.CONTEXT_PATH}/api/v1/payments"
        }) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun paymentsToSameAccountTranslatesToTransferToSameAccount() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 10_000L))
        and(initiatePaymentReturnsErrorStatus(400, """{"message":"7980"}"""))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "self-transfer-key")
                .header("Content-Type", "application/json")
                .body(transferBody(fromAccountNumber(), 2_500L))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7985"
            }
        }
    }

    @Test
    fun supportAgentJwtOnTransferReturns403AndDoesNotCallPayments() = malaiAcceptanceTest {
        given(seededOwnAccountsWithStartingBalance(startingBalanceMinor = 10_000L))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts/${someAccountUid()}/transfers")
                .header("Idempotency-Key", "rbac-support-agent-key")
                .header("Content-Type", "application/json")
                .body(transferBody(toAccountNumber(), 2_500L))
                .withJwtTokenForUserIdAndAccountHolderId(
                    someUserId(),
                    someAccountHolderUid(),
                    listOf("support_agent"),
                )
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "${WithPaymentsApi.CONTEXT_PATH}/api/v1/payments"
        }) {
            this.shouldBeEmpty()
        }
    }
}
