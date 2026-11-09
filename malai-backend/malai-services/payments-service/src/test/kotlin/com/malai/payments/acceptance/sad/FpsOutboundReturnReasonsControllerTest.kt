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

class FpsOutboundReturnReasonsControllerTest : PaymentsAcceptanceTest() {

    private val externalAccountNumber = "99990050"

    @Test
    fun copVerdictOverrideDoesNotBypassTheLedgerFundsGuard() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(theNextGeneratedUuidWillBe(somePaymentUid(), randomUUID()))
        and(ownAccountsExistInLookup())
        and(theDestinationIsExternalAtSortCode(externalAccountNumber))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturnsInsufficientFunds())

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "fps-cop-override-insuf-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToSortCode(malaiSortCode)
                    .withToAccountNumber(externalAccountNumber)
                    .withAmountMinorUnits(5_000L)
                    .withCopVerdictOverride(true))
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

        and(theSubmittedFpsOutbounds()) {
            this shouldHaveSize 0
        }
    }
}
