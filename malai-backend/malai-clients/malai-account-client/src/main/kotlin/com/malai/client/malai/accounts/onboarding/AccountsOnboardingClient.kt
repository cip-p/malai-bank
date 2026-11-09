package com.malai.client.malai.accounts.onboarding

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.accounts.onboarding.model.CreateOnboardingAccountRequest
import com.malai.client.malai.accounts.onboarding.model.CreateOnboardingAccountResponse
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Calls the service-to-service onboarding endpoints on account-service. The receiving service
 * authenticates the request via [com.malai.security.ServiceJwtFilter].
 */
@Component
class AccountsOnboardingClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.account.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun createAccount(
        serviceToken: String,
        request: CreateOnboardingAccountRequest,
    ): CreateOnboardingAccountResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/onboarding/accounts")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 201)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/onboarding/accounts")
        return fromJson(responseBody, CreateOnboardingAccountResponse::class.java)
    }

    fun activateAccountsForUser(serviceToken: String, userId: UUID) {
        val httpRequest = HttpPost(
            "$malaiBaseUrl/api/v1/internal/onboarding/accounts/activate/$userId"
        )
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        makeRequest(httpRequest, 204)
    }
}
