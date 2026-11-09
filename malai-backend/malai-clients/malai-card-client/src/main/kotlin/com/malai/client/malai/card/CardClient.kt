package com.malai.client.malai.card

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.card.model.AccountAuthorisationsResponse
import com.malai.client.malai.card.model.AuthoriseCardRequest
import com.malai.client.malai.card.model.AuthoriseCardResponse
import com.malai.client.malai.card.model.CardDetailResponse
import com.malai.client.malai.card.model.CardsResponse
import com.malai.client.malai.card.model.InternalAuthorisationResponse
import com.malai.client.malai.card.model.InternalCardResponse
import com.malai.client.malai.card.model.IssueCardRequest
import com.malai.client.malai.card.model.IssueCardResponse
import com.malai.client.malai.card.model.StatementFiguresResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import tools.jackson.module.kotlin.jacksonMapperBuilder

@Component
class CardClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.card.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    private val requestMapper = jacksonMapperBuilder().changeDefaultPropertyInclusion { it.withValueInclusion(NON_NULL) }.build()

    fun issueCard(serviceToken: String, request: IssueCardRequest): IssueCardResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/cards")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/cards")
        return fromJson(responseBody, IssueCardResponse::class.java)
    }

    fun authorise(serviceToken: String, request: AuthoriseCardRequest): AuthoriseCardResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/cards/authorisations")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/cards/authorisations")
        return fromJson(responseBody, AuthoriseCardResponse::class.java)
    }

    fun listAccountAuthorisations(serviceToken: String, accountId: UUID): AccountAuthorisationsResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/cards/authorisations?accountId=$accountId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/cards/authorisations")
        return fromJson(responseBody, AccountAuthorisationsResponse::class.java)
    }

    fun getStatementFigures(
        serviceToken: String,
        closingBalanceMinorUnits: Long,
        cycleMonth: String,
    ): StatementFiguresResponse {
        val httpRequest = HttpGet(
            "$malaiBaseUrl/api/v1/internal/cards/statement-figures" +
                "?closingBalanceMinorUnits=$closingBalanceMinorUnits&cycleMonth=$cycleMonth"
        )
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/cards/statement-figures")
        return fromJson(responseBody, StatementFiguresResponse::class.java)
    }

    fun listCards(accessToken: String, accountHolderId: UUID): CardsResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/cards")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $accessToken")
        httpRequest.addHeader("MAL-Account-Holder-Id", accountHolderId.toString())

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/cards")
        return fromJson(responseBody, CardsResponse::class.java)
    }

    fun getCard(accessToken: String, accountHolderId: UUID, cardId: UUID): CardDetailResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/cards/$cardId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $accessToken")
        httpRequest.addHeader("MAL-Account-Holder-Id", accountHolderId.toString())

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/cards/$cardId")
        return fromJson(responseBody, CardDetailResponse::class.java)
    }

    fun resolveCard(serviceToken: String, cardId: UUID, accountHolderId: UUID): InternalCardResponse? {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/cards/$cardId?accountHolderId=$accountHolderId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        return getOrNull(httpRequest, InternalCardResponse::class.java)
    }

    fun resolveAuthorisation(serviceToken: String, authorisationId: UUID, accountHolderId: UUID): InternalAuthorisationResponse? {
        val httpRequest = HttpGet(
            "$malaiBaseUrl/api/v1/internal/cards/authorisations/$authorisationId?accountHolderId=$accountHolderId"
        )
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        return getOrNull(httpRequest, InternalAuthorisationResponse::class.java)
    }

    private fun <T> getOrNull(httpRequest: HttpGet, clazz: Class<T>): T? =
        try {
            val responseBody = makeRequest(httpRequest, 200) ?: return null
            fromJson(responseBody, clazz)
        } catch (ex: MalaiClientResponseException) {
            if (ex.statusCode == 404) null else throw ex
        }
}
