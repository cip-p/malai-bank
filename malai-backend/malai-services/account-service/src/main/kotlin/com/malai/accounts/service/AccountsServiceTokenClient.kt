package com.malai.accounts.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Account-service's Cognito service-token client. Reads `malai.cognito.account-service.*`,
 * which the malai-api aggregator and each per-service application.properties wire to the
 * shared `MAL_AWS_COGNITO_MALAI_SERVICE_*` env vars so `createAccount` can authenticate to
 * ledger-service's internal registration endpoint. Mirrors auth-service's
 * [com.malai.auth.service.AuthServiceTokenClient].
 */
@Component
class AccountsServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.account-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.account-service.client-id}") clientId: String,
    @Value("\${malai.cognito.account-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.account-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "account-service",
)
