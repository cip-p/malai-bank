package com.malai.client.malai.card.metrics

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.card.builders.response.CardsIssuedResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext

interface WithCardMetricsApi : WithWiremock {

    fun cardsIssuedReturns(response: CardsIssuedResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(ISSUED_COUNT_PATH))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun cardsIssuedFailsWithServerError() = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(ISSUED_COUNT_PATH))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun cardsIssuedTakesLongerThan(delayMillis: Int, response: CardsIssuedResponseBuilder) =
        Action<GivensContext> {
            wiremock().stubFor(
                get(urlPathEqualTo(ISSUED_COUNT_PATH))
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
        const val CONTEXT_PATH = "/card-service"
        const val ISSUED_COUNT_PATH = "$CONTEXT_PATH/api/v1/internal/cards/issued-count"
    }
}
