package com.malai.client.malai.accountholder.metrics

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.accountholder.builders.response.AccountHoldersCreatedResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext

/**
 * WireMock stubs for account-holder-service's service-to-service metric read.
 *
 * The stubs match on path only, not on the window, so a test that cares about the window asserts
 * on the captured request rather than encoding it twice.
 */
interface WithAccountHolderMetricsApi : WithWiremock {

    fun accountHoldersCreatedReturns(response: AccountHoldersCreatedResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(CREATED_COUNT_PATH))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun accountHoldersCreatedFailsWithServerError() = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(CREATED_COUNT_PATH))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun accountHoldersCreatedTakesLongerThan(delayMillis: Int, response: AccountHoldersCreatedResponseBuilder) =
        Action<GivensContext> {
            wiremock().stubFor(
                get(urlPathEqualTo(CREATED_COUNT_PATH))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(response.build())
                            .withFixedDelay(delayMillis)
                    )
            )
        }

    companion object {
        const val CONTEXT_PATH = "/account-holder-service"
        const val CREATED_COUNT_PATH = "$CONTEXT_PATH/api/v1/internal/account-holders/created-count"
    }
}
