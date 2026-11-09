package com.malai.client.malai.accounts.lookup

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

/**
 * WireMock stubs for account-service's internal account-lookup endpoints. Mixed into the
 * acceptance tests of any service that resolves accounts over the boundary (payments-service on
 * initiate / inbound), so outbound lookups are intercepted and their request shape can be asserted.
 *
 * An unstubbed account id / identifier falls through to WireMock's default 404, which the client
 * surfaces as `null` — exactly how an unknown beneficiary or a non-owned account reads.
 */
interface WithAccountLookup : WithWiremock {

    fun getAccountReturns(
        accountId: UUID,
        accountHolderId: UUID,
        currency: String = "GBP",
        sortCode: String = "65-82-37",
        accountNumber: String = "12345678",
        name: String = "Main Account",
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/accounts/$accountId"))
                .withQueryParam("accountHolderId", equalTo(accountHolderId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body(accountId, accountHolderId, currency, sortCode, accountNumber, name))
                )
        )
    }

    fun getAccountReturnsNotFound(
        accountId: UUID,
        accountHolderId: UUID,
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/accounts/$accountId"))
                .withQueryParam("accountHolderId", equalTo(accountHolderId.toString()))
                .willReturn(notFound())
        )
    }

    fun resolveAccountReturns(
        sortCode: String,
        accountNumber: String,
        accountId: UUID,
        accountHolderId: UUID,
        currency: String = "GBP",
        name: String = "Main Account",
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/accounts/by-identifier"))
                .withQueryParam("sortCode", equalTo(sortCode))
                .withQueryParam("accountNumber", equalTo(accountNumber))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body(accountId, accountHolderId, currency, sortCode, accountNumber, name))
                )
        )
    }

    fun resolveAccountReturnsNotFound(
        sortCode: String,
        accountNumber: String,
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/accounts/by-identifier"))
                .withQueryParam("sortCode", equalTo(sortCode))
                .withQueryParam("accountNumber", equalTo(accountNumber))
                .willReturn(notFound())
        )
    }

    private fun body(
        accountId: UUID,
        accountHolderId: UUID,
        currency: String,
        sortCode: String,
        accountNumber: String,
        name: String,
    ): String =
        """{"accountId":"$accountId","accountHolderId":"$accountHolderId","currency":"$currency","sortCode":"$sortCode","accountNumber":"$accountNumber","name":"$name"}"""

    companion object {
        const val CONTEXT_PATH = "/account-service"
    }
}
