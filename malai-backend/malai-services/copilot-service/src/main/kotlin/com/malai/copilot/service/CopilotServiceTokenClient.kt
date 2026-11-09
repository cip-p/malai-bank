package com.malai.copilot.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class CopilotServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.copilot-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.copilot-service.client-id}") clientId: String,
    @Value("\${malai.cognito.copilot-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.copilot-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "copilot-service",
)
