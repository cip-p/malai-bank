package com.malai.auth.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Auth-service's Cognito service-token client. Reads `malai.cognito.auth-service.*`
 * properties, populated from the shared `MAL_AWS_COGNITO_MALAI_SERVICE_*` env vars that
 * `ecs-malai-api` injects from the `${prefix}-malai-service-cognito-service` secret.
 *
 * Used by [RegistrationOrchestrator] to authenticate to account-holder-service and
 * account-service on `/api/v1/internal/onboarding/...`.
 */
@Component
class AuthServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.auth-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.auth-service.client-id}") clientId: String,
    @Value("\${malai.cognito.auth-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.auth-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "auth-service",
)
