package com.malai.payments.acceptance.sad

import com.malai.client.malai.payments.builders.request.InternalInitiatePaymentRequestBuilder.Companion.anInternalInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID

class InternalPaymentsControllerTest : PaymentsAcceptanceTest() {

    private val scheduledEndToEndId: UUID = UUID.fromString("00000000-0000-4000-9000-0000000e2e02")

    @Test
    fun insufficientFundsSurfacesAsADistinctFailureAndNothingIsRecorded() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePaymentUid()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturnsInsufficientFunds())
        val payeeAccountNumber = "87651236"
        and(anotherCustomersAccountExists(payeeAccountNumber))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/internal/payments")
                .header("Content-Type", "application/json")
                .body(anInternalInitiatePaymentRequest()
                    .withEndToEndId(scheduledEndToEndId)
                    .withFromAccountId(someAccountUid())
                    .withAccountHolderId(someAccountHolderUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(payeeAccountNumber)
                    .withToName("Rent Collector")
                    .withAmountMinorUnits(9_999_999L)
                    .withReference("rent"))
                .withServiceJwtToken()
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

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun withoutAServiceTokenTheEndpointIsUnreachable() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/internal/payments")
                .header("Content-Type", "application/json")
                .body(anInternalInitiatePaymentRequest()
                    .withEndToEndId(scheduledEndToEndId)
                    .withFromAccountId(someAccountUid())
                    .withAccountHolderId(someAccountHolderUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber("87651237")
                    .withAmountMinorUnits(2_500L)
                    .withReference("rent"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun readingAnUnknownPaymentIsNotFound() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())

        whenever(paymentsServiceReceives(
            get().url("/api/v1/internal/payments/${somePaymentUid()}").withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8989"
                noExtraProps()
            }
        }
    }

    @Test
    fun withoutAServiceTokenThePaymentReadIsUnreachable() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get().url("/api/v1/internal/payments/${somePaymentUid()}")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun withoutAServiceTokenTheSettledWindowReadIsUnreachable() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/settled")
                .queryParam("from", "2026-10-15T00:00:00Z")
                .queryParam("until", "2026-10-16T00:00:00Z")
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
    fun withACustomerTokenTheSettledWindowReadIsUnreachable() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/settled")
                .queryParam("from", "2026-10-15T00:00:00Z")
                .queryParam("until", "2026-10-16T00:00:00Z")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5987"
                noExtraProps()
            }
        }
    }

    @Test
    fun aSettledWindowMissingItsUpperBoundIsRejected() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/settled")
                .queryParam("from", "2026-10-15T00:00:00Z")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun anUnparseableSettledWindowBoundIsRejected() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/internal/payments/settled")
                .queryParam("from", "yesterday-ish")
                .queryParam("until", "2026-10-16T00:00:00Z")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun withACustomerTokenTheEndpointIsUnreachable() = malaiAcceptanceTest {
        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/internal/payments")
                .header("Content-Type", "application/json")
                .body(anInternalInitiatePaymentRequest()
                    .withEndToEndId(scheduledEndToEndId)
                    .withFromAccountId(someAccountUid())
                    .withAccountHolderId(someAccountHolderUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber("87651237")
                    .withAmountMinorUnits(2_500L)
                    .withReference("rent"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }
}
