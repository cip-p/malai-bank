package com.malai.client.malai.card

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.malai.client.malai.card.builders.response.AccountAuthorisationsResponseBuilder
import com.malai.client.malai.card.builders.response.AuthoriseCardResponseBuilder
import com.malai.client.malai.card.builders.response.CardDetailResponseBuilder
import com.malai.client.malai.card.builders.response.CardsResponseBuilder
import com.malai.client.malai.card.builders.response.IssueCardResponseBuilder
import com.malai.client.malai.card.builders.response.StatementFiguresResponseBuilder
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

interface WithCards : WithWiremock {

    fun resolveCardReturns(
        cardId: UUID,
        accountHolderId: UUID,
        accountId: UUID,
        pan: String,
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/$cardId"))
                .withQueryParam("accountHolderId", equalTo(accountHolderId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"cardId":"$cardId","accountId":"$accountId","pan":"$pan"}""")
                )
        )
    }

    fun resolveCardReturnsNotFound(cardId: UUID, accountHolderId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/$cardId"))
                .withQueryParam("accountHolderId", equalTo(accountHolderId.toString()))
                .willReturn(notFound())
        )
    }

    fun resolveAuthorisationReturns(
        authorisationId: UUID,
        accountHolderId: UUID,
        schemeReference: UUID,
        status: String,
        amountMinorUnits: Long,
        currency: String = "GBP",
    ) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/authorisations/$authorisationId"))
                .withQueryParam("accountHolderId", equalTo(accountHolderId.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"authorisationId":"$authorisationId","schemeReference":"$schemeReference",""" +
                                """"status":"$status","amountMinorUnits":$amountMinorUnits,"currency":"$currency"}"""
                        )
                )
        )
    }

    fun resolveAuthorisationReturnsNotFound(authorisationId: UUID, accountHolderId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/authorisations/$authorisationId"))
                .withQueryParam("accountHolderId", equalTo(accountHolderId.toString()))
                .willReturn(notFound())
        )
    }

    fun issueCardReturns(response: IssueCardResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun issueCardReturnsServerError() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards"))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun authoriseCardReturns(response: AuthoriseCardResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/authorisations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun listAccountAuthorisationsReturns(response: AccountAuthorisationsResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/authorisations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun listAccountAuthorisationsReturnsServerError() = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/authorisations"))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun getStatementFiguresReturns(response: StatementFiguresResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/statement-figures"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun getStatementFiguresReturnsServerError() = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/cards/statement-figures"))
                .willReturn(aResponse().withStatus(500))
        )
    }

    fun listCardsReturns(response: CardsResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/cards"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun getCardReturns(response: CardDetailResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathMatching("$CONTEXT_PATH/api/v1/cards/[^/]+"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    companion object {
        const val CONTEXT_PATH = "/card-service"
    }
}
