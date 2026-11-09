package com.malai.client.malai.mdr

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.mdr.model.ActionableOutputResponse
import com.malai.client.malai.mdr.model.DecisionRecordResponse
import com.malai.client.malai.mdr.model.RecordModelDecisionRequest
import com.malai.client.malai.mdr.model.RecordModelDecisionResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import tools.jackson.module.kotlin.jacksonMapperBuilder

@Component
class MdrClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.mdr.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    private val requestMapper = jacksonMapperBuilder().changeDefaultPropertyInclusion { it.withValueInclusion(NON_NULL) }.build()

    fun recordModelDecision(
        serviceToken: String,
        request: RecordModelDecisionRequest,
    ): RecordModelDecisionResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/mdr/decisions")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request))

        val responseBody = makeRequest(httpRequest, 201)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/mdr/decisions")
        return fromJson(responseBody, RecordModelDecisionResponse::class.java)
    }

    fun decisionRecord(serviceToken: String, decisionId: UUID): DecisionRecordResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/mdr/decisions/$decisionId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/mdr/decisions/$decisionId")
        return fromJson(responseBody, DecisionRecordResponse::class.java)
    }

    fun actionableOutput(serviceToken: String, decisionId: UUID): ActionableOutputResponse? {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/mdr/decisions/$decisionId/actionable-output")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        return try {
            val responseBody = makeRequest(httpRequest, 200, TOLERATED_GATE_REFUSAL) ?: return null
            fromJson(responseBody, ActionableOutputResponse::class.java)
        } catch (ex: MalaiClientResponseException) {
            if (ex.statusCode == GATE_REFUSED) null else throw ex
        }
    }

    companion object {
        private const val GATE_REFUSED = 409
        private val TOLERATED_GATE_REFUSAL = setOf(GATE_REFUSED)
    }
}
