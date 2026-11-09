package com.malai.accounts.acceptance.sad

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.builders.request.ConfirmationOfPayeeRequestBuilder.Companion.aConfirmationOfPayeeRequest
import com.malai.client.malai.stub.cop.builders.response.StubCopLookupResponseBuilder.Companion.aStubCopLookupResponse
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import org.springframework.jdbc.core.JdbcTemplate

class ConfirmationOfPayeeControllerTest : AccountsAcceptanceTest() {

    private fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    @Test
    fun postReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-no-jwt-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun postWithoutIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest())
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7991"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReplayWithDifferentBodyReturns422() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-mismatch-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Tesco"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-mismatch-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Different"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7990"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturns400WhenSortCodeIsInvalid() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-invalid-sort-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withSortCode("200000"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturns400WhenAccountNumberIsNotEightDigits() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-invalid-account-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withAccountNumber("123"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturns400WhenNameIsBlank() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-blank-name-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName(""))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }
}
