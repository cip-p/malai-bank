package com.malai.client.malai.ledger

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.ledger.model.BalancesResponse
import com.malai.client.malai.ledger.model.EntriesResponse
import com.malai.client.malai.ledger.model.HoldResponse
import com.malai.client.malai.ledger.model.PlaceHoldRequest
import com.malai.client.malai.ledger.model.PostTransactionRequest
import com.malai.client.malai.ledger.model.PostTransactionResponse
import com.malai.client.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.client.malai.ledger.model.RegisterLedgerAccountResponse
import com.malai.client.malai.ledger.model.ReleaseHoldRequest
import com.malai.client.malai.ledger.model.SettleHoldRequest
import com.malai.client.malai.ledger.model.SettleHoldResponse
import com.malai.client.malai.ledger.model.TracedTransactionsResponse
import com.malai.client.malai.ledger.model.TransactionEntriesResponse
import com.malai.commons.exception.MalaiCode
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import tools.jackson.module.kotlin.jacksonMapperBuilder

/**
 * Calls the internal-only ledger-service. The ledger is the bank's book of record reached
 * over a synchronous HTTP boundary — the Mambu-swappable seam. The receiving service
 * authenticates the request via [com.malai.security.ServiceJwtFilter] when bundled in malai-api.
 *
 * Posting is idempotent on the caller-supplied transaction id; the funds guard lives in the
 * ledger, so callers never pre-check — they handle a [LedgerInsufficientFundsException].
 */
@Component
class LedgerClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.ledger.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    private val errorBodyMapper = jacksonMapperBuilder().configure(FAIL_ON_UNKNOWN_PROPERTIES, false).build()
    private val requestMapper = jacksonMapperBuilder().changeDefaultPropertyInclusion { it.withValueInclusion(NON_NULL) }.build()

    fun registerLedgerAccount(
        serviceToken: String,
        request: RegisterLedgerAccountRequest,
    ): RegisterLedgerAccountResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/ledger/accounts")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/ledger/accounts")
        return fromJson(responseBody, RegisterLedgerAccountResponse::class.java)
    }

    fun postTransaction(
        serviceToken: String,
        request: PostTransactionRequest,
    ): PostTransactionResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/ledger/transactions")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request))

        try {
            val responseBody = makeRequest(httpRequest, 200)
                ?: throw RuntimeException("expected a response body from POST /api/v1/internal/ledger/transactions")
            return fromJson(responseBody, PostTransactionResponse::class.java)
        } catch (ex: MalaiClientResponseException) {
            if (isInsufficientFunds(ex.responseBody)) {
                throw LedgerInsufficientFundsException(ex.responseBody)
            }
            throw ex
        }
    }

    fun placeHold(
        serviceToken: String,
        request: PlaceHoldRequest,
    ): HoldResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/ledger/holds")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request))

        try {
            val responseBody = makeRequest(httpRequest, 200)
                ?: throw RuntimeException("expected a response body from POST /api/v1/internal/ledger/holds")
            return fromJson(responseBody, HoldResponse::class.java)
        } catch (ex: MalaiClientResponseException) {
            if (isInsufficientFunds(ex.responseBody)) {
                throw LedgerInsufficientFundsException(ex.responseBody)
            }
            throw ex
        }
    }

    fun settleHold(
        serviceToken: String,
        holdId: UUID,
        posting: PostTransactionRequest,
        settleAmountMinorUnits: Long? = null,
    ): SettleHoldResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/ledger/holds/$holdId/settle")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(posting.toSettleHoldRequest(settleAmountMinorUnits)))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/ledger/holds/$holdId/settle")
        return fromJson(responseBody, SettleHoldResponse::class.java)
    }

    private fun PostTransactionRequest.toSettleHoldRequest(amountMinorUnits: Long?) = SettleHoldRequest(
        transactionId = transactionId,
        type = type,
        initiatedById = initiatedById,
        postedAt = postedAt,
        description = description,
        traceId = traceId,
        amountMinorUnits = amountMinorUnits,
        entries = entries,
    )

    fun releaseHold(
        serviceToken: String,
        holdId: UUID,
        request: ReleaseHoldRequest,
    ): HoldResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/ledger/holds/$holdId/release")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(requestMapper.writeValueAsString(request))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/ledger/holds/$holdId/release")
        return fromJson(responseBody, HoldResponse::class.java)
    }

    /**
     * One hold, whole. A caller reconstructing why funds were reserved needs the amount, the
     * status and the moment it was resolved — figures the balances envelope sums away.
     */
    fun getHold(
        serviceToken: String,
        holdId: UUID,
    ): HoldResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/ledger/holds/$holdId")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/ledger/holds/$holdId")
        return fromJson(responseBody, HoldResponse::class.java)
    }

    fun getBalances(
        serviceToken: String,
        accountId: UUID,
    ): BalancesResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/ledger/accounts/$accountId/balances")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/ledger/accounts/$accountId/balances")
        return fromJson(responseBody, BalancesResponse::class.java)
    }

    fun listEntries(
        serviceToken: String,
        accountId: UUID,
        periodStart: String? = null,
        periodEnd: String? = null,
        limit: Int? = null,
    ): EntriesResponse {
        val params = buildList {
            if (periodStart != null) add("periodStart=${urlEncode(periodStart)}")
            if (periodEnd != null) add("periodEnd=${urlEncode(periodEnd)}")
            if (limit != null) add("limit=$limit")
        }
        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/ledger/accounts/$accountId/entries$query")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from GET /api/v1/internal/ledger/accounts/$accountId/entries")
        return fromJson(responseBody, EntriesResponse::class.java)
    }

    /**
     * The legs of one transaction, under the transaction's own facts. Each entry names its own
     * account, because the legs of a double entry differ only by account and sign.
     */
    fun listTransactionEntries(
        serviceToken: String,
        transactionId: UUID,
    ): TransactionEntriesResponse {
        val httpRequest = HttpGet("$malaiBaseUrl/api/v1/internal/ledger/transactions/$transactionId/entries")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException(
                "expected a response body from GET /api/v1/internal/ledger/transactions/$transactionId/entries"
            )
        return fromJson(responseBody, TransactionEntriesResponse::class.java)
    }

    fun listTransactionsForTrace(
        serviceToken: String,
        traceId: String,
    ): TracedTransactionsResponse {
        val httpRequest = HttpGet(
            "$malaiBaseUrl/api/v1/internal/ledger/transactions?traceId=${urlEncode(traceId)}"
        )
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException(
                "expected a response body from GET /api/v1/internal/ledger/transactions?traceId=$traceId"
            )
        return fromJson(responseBody, TracedTransactionsResponse::class.java)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun isInsufficientFunds(responseBody: String?): Boolean {
        if (responseBody == null) return false
        return runCatching {
            errorBodyMapper.readValue(responseBody, ErrorBody::class.java).message
        }.getOrNull() == MalaiCode.LEDGER_INSUFFICIENT_FUNDS.code.toString()
    }

    private data class ErrorBody(val message: String?)
}
