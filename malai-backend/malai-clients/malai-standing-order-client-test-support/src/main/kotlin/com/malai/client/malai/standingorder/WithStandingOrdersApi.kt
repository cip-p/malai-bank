package com.malai.client.malai.standingorder

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.standingorder.builders.response.ActiveStandingOrdersResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

interface WithStandingOrdersApi : WithWiremock {

    fun getActiveStandingOrdersReturns(
        accountId: UUID,
        response: ActiveStandingOrdersResponseBuilder,
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/standing-orders"))
                .withQueryParam("accountId", equalTo(accountId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build()),
                ),
        )
    }

    fun getActiveStandingOrdersReturnsServerError(accountId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/standing-orders"))
                .withQueryParam("accountId", equalTo(accountId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "standing-order-service unavailable"}"""),
                ),
        )
    }

    companion object {
        const val CONTEXT_PATH = "/standing-order-service"
    }
}
