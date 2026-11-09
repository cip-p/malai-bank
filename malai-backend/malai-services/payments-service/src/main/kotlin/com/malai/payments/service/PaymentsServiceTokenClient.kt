package com.malai.payments.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Payments-service's Cognito service-token client. Reads `malai.cognito.payments-service.*`,
 * which the malai-api aggregator and each per-service application.properties wire to the
 * shared `MAL_AWS_COGNITO_MALAI_SERVICE_*` env vars so the book-transfer path can authenticate
 * to ledger-service's internal posting endpoint. Mirrors auth-service's
 * [com.malai.auth.service.AuthServiceTokenClient].
 */
@Component
class PaymentsServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.payments-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.payments-service.client-id}") clientId: String,
    @Value("\${malai.cognito.payments-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.payments-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "payments-service",
)
