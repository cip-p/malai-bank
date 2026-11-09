package com.malai.exec.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Exec-service's Cognito service-token client, so the fan-out can authenticate to each owning
 * context's internal metric read. Mirrors payments-service's
 * [com.malai.payments.service.PaymentsServiceTokenClient].
 */
@Component
class ExecServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.exec-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.exec-service.client-id}") clientId: String,
    @Value("\${malai.cognito.exec-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.exec-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "exec-service",
)
