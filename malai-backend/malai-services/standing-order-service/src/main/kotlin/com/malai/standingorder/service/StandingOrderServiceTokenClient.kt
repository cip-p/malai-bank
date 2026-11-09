package com.malai.standingorder.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Standing-order-service's Cognito service-token client. Reads
 * `malai.cognito.standing-order-service.*`, which each per-service application.properties and the
 * malai-api aggregator wire to the shared `MAL_AWS_COGNITO_MALAI_SERVICE_*` env vars, so the
 * executor can authenticate to payments-service's internal scheduled-initiation endpoint.
 * Mirrors [com.malai.payments.service.PaymentsServiceTokenClient].
 */
@Component
class StandingOrderServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.standing-order-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.standing-order-service.client-id}") clientId: String,
    @Value("\${malai.cognito.standing-order-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.standing-order-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "standing-order-service",
)
