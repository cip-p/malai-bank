package com.malai.client.malai.directdebit

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.directdebit.builders.response.ActiveDirectDebitsResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

interface WithDirectDebitsApi : WithWiremock {

    fun getActiveDirectDebitsReturns(
        accountId: UUID,
        response: ActiveDirectDebitsResponseBuilder,
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/direct-debits"))
                .withQueryParam("accountId", equalTo(accountId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build()),
                ),
        )
    }

    fun getActiveDirectDebitsReturnsServerError(accountId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/direct-debits"))
                .withQueryParam("accountId", equalTo(accountId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "direct-debit-service unavailable"}"""),
                ),
        )
    }

    companion object {
        const val CONTEXT_PATH = "/direct-debit-service"
    }
}
