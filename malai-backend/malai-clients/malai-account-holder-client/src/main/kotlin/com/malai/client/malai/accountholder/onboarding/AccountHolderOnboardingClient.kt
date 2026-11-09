package com.malai.client.malai.accountholder.onboarding

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAccountHolderRequest
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAccountHolderResponse
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAddressRequest
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingIndividualRequest
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Calls the service-to-service onboarding endpoints on account-holder-service.
 *
 * Each method takes a Cognito service (`client_credentials`) access token; the receiving service
 * validates it via [com.malai.security.ServiceJwtFilter]. Token acquisition + caching is the
 * caller's responsibility (see `ServiceTokenClient` in auth-service).
 */
@Component
class AccountHolderOnboardingClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.account-holder.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun createAccountHolder(
        serviceToken: String,
        request: CreateOnboardingAccountHolderRequest,
    ): CreateOnboardingAccountHolderResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/onboarding/account-holders")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 201)
            ?: throw RuntimeException("expected a response body from POST /api/v1/internal/onboarding/account-holders")
        return fromJson(responseBody, CreateOnboardingAccountHolderResponse::class.java)
    }

    fun createIndividual(
        serviceToken: String,
        accountHolderId: UUID,
        request: CreateOnboardingIndividualRequest,
    ) {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/onboarding/account-holders/$accountHolderId/individual")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(toJson(request))
        makeRequest(httpRequest, 204)
    }

    fun createAddress(
        serviceToken: String,
        accountHolderId: UUID,
        request: CreateOnboardingAddressRequest,
    ) {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/onboarding/account-holders/$accountHolderId/address")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        httpRequest.entity = StringEntity(toJson(request))
        makeRequest(httpRequest, 204)
    }

    fun activateByUserId(serviceToken: String, userId: UUID) {
        val httpRequest = HttpPost("$malaiBaseUrl/api/v1/internal/onboarding/account-holders/activate/$userId")
        httpRequest.addHeader("Authorization", "Bearer $serviceToken")
        makeRequest(httpRequest, 204)
    }
}
