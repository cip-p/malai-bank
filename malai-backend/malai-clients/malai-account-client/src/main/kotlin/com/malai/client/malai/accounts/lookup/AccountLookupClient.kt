package com.malai.client.malai.accounts.lookup

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.accounts.lookup.model.InternalAccountResponse
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Service-to-service account lookups against account-service's internal endpoints. Used by
 * payments-service to resolve the payer (by id, scoped to the authenticated account holder) and
 * the beneficiary (by sort-code + account-number) without sharing account-service's persistence.
 * The receiving endpoints are authenticated via [com.malai.security.ServiceJwtFilter].
 *
 * A `404` means "no such account for this caller's scope" and is surfaced as `null` so callers can
 * branch (e.g. an unknown beneficiary becomes an external FPS payment or an INVALID_ACCOUNT return).
 */
@Component
class AccountLookupClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.account.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun getAccount(
        serviceToken: String,
        accountId: UUID,
        accountHolderId: UUID,
    ): InternalAccountResponse? {
        val httpRequest = HttpGet(
            "$malaiBaseUrl/api/v1/internal/accounts/$accountId?accountHolderId=$accountHolderId",
        )
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        return getOrNull(httpRequest)
    }

    fun resolveBySortCodeAndAccountNumber(
        serviceToken: String,
        sortCode: String,
        accountNumber: String,
    ): InternalAccountResponse? {
        val httpRequest = HttpGet(
            "$malaiBaseUrl/api/v1/internal/accounts/by-identifier" +
                "?sortCode=${urlEncode(sortCode)}&accountNumber=${urlEncode(accountNumber)}",
        )
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        return getOrNull(httpRequest)
    }

    private fun getOrNull(httpRequest: HttpGet): InternalAccountResponse? =
        try {
            val responseBody = makeRequest(httpRequest, 200) ?: return null
            fromJson(responseBody, InternalAccountResponse::class.java)
        } catch (ex: MalaiClientResponseException) {
            if (ex.statusCode == 404) null else throw ex
        }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
