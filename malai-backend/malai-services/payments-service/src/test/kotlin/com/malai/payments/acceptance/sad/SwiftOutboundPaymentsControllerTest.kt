package com.malai.payments.acceptance.sad

import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import java.util.UUID.randomUUID

class SwiftOutboundPaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun mixingUkAndInternationalIdentifiersReturnsBadRequest() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
        and(theNextGeneratedUuidWillBe(somePaymentUid(), randomUUID(), someTransferUid()))
        and(ownAccountsExistInLookup())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "mixed-1")
                .header("Content-Type", "application/json")
                .body("""{"fromAccountId":"${someAccountUid()}","toSortCode":"$malaiSortCode","toAccountNumber":"12345678","toIban":"DE89370400440532013000","toBic":"COBADEFFXXX","amountMinorUnits":1000,"currency":"EUR"}""")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7978"
            }
        }

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 0
        }

        and(theSubmittedSwiftOutbounds()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun swiftWhenTheLedgerRejectsTheHoldReturns400AndDoesNotPublish() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
        and(theNextGeneratedUuidWillBe(somePaymentUid(), randomUUID()))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturnsInsufficientFunds())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "swift-insuf-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToIban("DE89370400440532013000")
                    .withToBic("COBADEFFXXX")
                    .withAmountMinorUnits(2_500L)
                    .withCurrency("EUR"))
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

        and(theSubmittedSwiftOutbounds()) {
            this shouldHaveSize 0
        }
    }
}
