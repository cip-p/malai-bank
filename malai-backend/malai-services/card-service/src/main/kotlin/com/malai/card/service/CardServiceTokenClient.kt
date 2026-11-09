package com.malai.card.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class CardServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.card-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.card-service.client-id}") clientId: String,
    @Value("\${malai.cognito.card-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.card-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "card-service",
)
