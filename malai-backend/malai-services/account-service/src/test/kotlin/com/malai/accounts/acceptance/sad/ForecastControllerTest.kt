package com.malai.accounts.acceptance.sad

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.client.malai.directdebit.builders.response.ActiveDirectDebitsResponseBuilder.ActiveDirectDebitBuilder.Companion.anActiveDirectDebit
import com.malai.client.malai.directdebit.builders.response.ActiveDirectDebitsResponseBuilder.Companion.anActiveDirectDebitsResponse
import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder.Companion.aBalancesResponse
import com.malai.client.malai.standingorder.builders.response.ActiveStandingOrdersResponseBuilder.ActiveStandingOrderBuilder.Companion.anActiveStandingOrder
import com.malai.client.malai.standingorder.builders.response.ActiveStandingOrdersResponseBuilder.Companion.anActiveStandingOrdersResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE

class ForecastControllerTest : AccountsAcceptanceTest() {

    @Test
    fun getForecastReturns404WhenAccountBelongsToAnotherAccountHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            userId = anotherUserId(),
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/forecast")
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
    fun getForecastFailsClosedWith503WhenInsightsUnavailable() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withAvailableBalanceMinorUnits(100_000L)))
        and(getActiveStandingOrdersReturns(
            someAccountUid(),
            anActiveStandingOrdersResponse().withStandingOrders(
                anActiveStandingOrder().withAccountId(someAccountUid()),
            )))
        and(getActiveDirectDebitsReturns(
            someAccountUid(),
            anActiveDirectDebitsResponse().withDirectDebits(
                anActiveDirectDebit().withAccountId(someAccountUid()),
            )))
        and(balanceForecastReturnsServerError())

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/forecast")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe SERVICE_UNAVAILABLE
            body containsProp json {
                "message" To "3998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getForecastFailsClosedWith503WhenStandingOrderServiceUnavailable() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withAvailableBalanceMinorUnits(100_000L)))
        and(getActiveStandingOrdersReturnsServerError(someAccountUid()))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/forecast")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe SERVICE_UNAVAILABLE
            body containsProp json {
                "message" To "3998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getForecastFailsClosedWith503WhenDirectDebitServiceUnavailable() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withAvailableBalanceMinorUnits(100_000L)))
        and(getActiveStandingOrdersReturns(
            someAccountUid(),
            anActiveStandingOrdersResponse().withStandingOrders(
                anActiveStandingOrder().withAccountId(someAccountUid()),
            )))
        and(getActiveDirectDebitsReturnsServerError(someAccountUid()))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/forecast")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe SERVICE_UNAVAILABLE
            body containsProp json {
                "message" To "3998"
                noExtraProps()
            }
        }
    }
}
