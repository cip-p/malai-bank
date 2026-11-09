package com.malai.client.malai.mdr

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.client.malai.mdr.builders.response.ActionableOutputResponseBuilder
import com.malai.client.malai.mdr.builders.response.DecisionRecordResponseBuilder
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

interface WithMdrApi : WithWiremock {

    fun recordModelDecisionReturns(response: RecordModelDecisionResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/mdr/decisions"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun recordModelDecisionFailsWithServerError() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/mdr/decisions"))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun decisionRecordReturns(decisionId: UUID, response: DecisionRecordResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(decisionRecordPath(decisionId)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun decisionRecordIsNotFound(decisionId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(decisionRecordPath(decisionId)))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message":"8977"}""")
                )
        )
    }

    fun decisionRecordFailsWithServerError(decisionId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(decisionRecordPath(decisionId)))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun actionableOutputReturns(decisionId: UUID, response: ActionableOutputResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(actionableOutputPath(decisionId)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun actionableOutputIsRefused(decisionId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(actionableOutputPath(decisionId)))
                .willReturn(
                    aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message":"6991"}""")
                )
        )
    }

    fun actionableOutputFailsWithServerError(decisionId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo(actionableOutputPath(decisionId)))
                .willReturn(aResponse().withStatus(500))
        )
    }

    private fun actionableOutputPath(decisionId: UUID) =
        "$CONTEXT_PATH/api/v1/internal/mdr/decisions/$decisionId/actionable-output"

    private fun decisionRecordPath(decisionId: UUID) =
        "$CONTEXT_PATH/api/v1/internal/mdr/decisions/$decisionId"

    companion object {
        const val CONTEXT_PATH = "/mdr-service"
    }
}
