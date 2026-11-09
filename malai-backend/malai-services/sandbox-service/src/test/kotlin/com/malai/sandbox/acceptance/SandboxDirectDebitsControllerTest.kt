package com.malai.sandbox.acceptance

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.malai.client.malai.directdebit.WithDirectDebitLodgement
import com.malai.client.malai.stub.directory.builders.response.CounterpartiesResponseBuilder.Companion.aCounterpartiesResponse
import com.malai.client.malai.stub.directory.builders.response.CounterpartyResponseBuilder.Companion.aCounterpartyResponse
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED

/**
 * SO/DD lifecycle issue 09 — the sandbox mandate-lodgement surface. Resolves the target account
 * against the active holder (ownership), snapshots the creditor from the fictional counterparty
 * directory, and asserts the outgoing lodgement call to direct-debit-service's internal endpoint.
 */
class SandboxDirectDebitsControllerTest : SandboxAcceptanceTest() {

    @Test
    fun lodgesMandateResolvingAccountAndCreditorThenReturnsTheMandate() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(getAccountReturns(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            currency = "GBP",
            sortCode = "65-82-37",
            accountNumber = "55667788",
            name = "Main Account",
        ))
        and(getCounterpartiesReturns(
            aCounterpartiesResponse().withCounterparties(
                aCounterpartyResponse()
                    .withSortCode("60-00-01")
                    .withAccountNumber("22334455")
                    .withBank("NatWest")
                    .withNameOnRecord("British Gas")
                    .withOutboundOutcome("SETTLES"),
            )
        ))
        and(lodgeMandateReturns(
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            creditorName = "British Gas",
            creditorSortCode = "60-00-01",
            creditorAccountNumber = "22334455",
            reference = "DD-GAS",
            amountMinorUnits = 4500,
            currency = "GBP",
            frequency = "MONTHLY",
            status = "ACTIVE",
            startDate = "2026-06-01",
            nextCollectionAt = "2026-06-01T00:00:00Z",
        ))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/direct-debits")
                .header("Content-Type", "application/json")
                .body(aLodgementRequestBody(creditorSortCode = "60-00-01", creditorAccountNumber = "22334455"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "directDebitId" To someMandateUid().toString()
                "accountId" To someAccountUid().toString()
                "creditorName" To "British Gas"
                "creditorSortCode" To "60-00-01"
                "creditorAccountNumber" To "22334455"
                "reference" To "DD-GAS"
                "amountMinorUnits" To 4500
                "currency" To "GBP"
                "frequency" To "MONTHLY"
                "status" To "ACTIVE"
                "startDate" To "2026-06-01"
                "nextCollectionAt" To "2026-06-01T00:00:00Z"
                noExtraProps()
            }
        }

        and(theDirectDebitServiceReceivedTheLodgement())
    }

    @Test
    fun returns404WhenAccountNotOwnedByHolder() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(getAccountReturnsNotFound(someAccountUid(), someAccountHolderUid()))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/direct-debits")
                .header("Content-Type", "application/json")
                .body(aLodgementRequestBody(creditorSortCode = "60-00-01", creditorAccountNumber = "22334455"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun returns404WhenCreditorNotInDirectory() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(getAccountReturns(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            currency = "GBP",
            sortCode = "65-82-37",
            accountNumber = "55667788",
            name = "Main Account",
        ))
        and(getCounterpartiesReturns(
            aCounterpartiesResponse().withCounterparties(
                aCounterpartyResponse()
                    .withSortCode("60-00-01")
                    .withAccountNumber("99999999")
                    .withBank("NatWest")
                    .withNameOnRecord("Someone Else")
                    .withOutboundOutcome("SETTLES"),
            )
        ))

        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/direct-debits")
                .header("Content-Type", "application/json")
                .body(aLodgementRequestBody(creditorSortCode = "60-00-01", creditorAccountNumber = "22334455"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8978"
                noExtraProps()
            }
        }
    }

    @Test
    fun returns401WhenUnauthenticated() = malaiAcceptanceTest {
        whenever(sandboxServiceReceives(
            post()
                .url("/api/v1/sandbox/direct-debits")
                .header("Content-Type", "application/json")
                .body(aLodgementRequestBody(creditorSortCode = "60-00-01", creditorAccountNumber = "22334455"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    private fun aLodgementRequestBody(creditorSortCode: String, creditorAccountNumber: String): String =
        """{"accountId":"${someAccountUid()}","creditorSortCode":"$creditorSortCode","creditorAccountNumber":"$creditorAccountNumber","amountMinorUnits":4500,"reference":"DD-GAS","frequency":"MONTHLY","startDate":"2026-06-01"}"""

    private fun theDirectDebitServiceReceivedTheLodgement() = CallableAction {
        wiremock().verify(
            postRequestedFor(urlEqualTo("${WithDirectDebitLodgement.CONTEXT_PATH}/api/v1/internal/direct-debits"))
                .withRequestBody(equalToJson(expectedLodgeBody()))
        )
    }

    private fun expectedLodgeBody(): String =
        """{"accountId":"${someAccountUid()}","creditorName":"British Gas","creditorSortCode":"60-00-01","creditorAccountNumber":"22334455","reference":"DD-GAS","amountMinorUnits":4500,"currency":"GBP","frequency":"MONTHLY","startDate":"2026-06-01"}"""
}
