package com.malai.aml.service

import com.malai.client.malai.ServiceTokenClient
import com.malai.commons.TimeService
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AmlServiceTokenClient(
    httpClient: CloseableHttpClient,
    timeService: TimeService,
    @Value("\${malai.cognito.aml-service.token-endpoint-url}") tokenEndpointUrl: String,
    @Value("\${malai.cognito.aml-service.client-id}") clientId: String,
    @Value("\${malai.cognito.aml-service.client-secret}") clientSecret: String,
    @Value("\${malai.cognito.aml-service.scope}") scope: String,
) : ServiceTokenClient(
    httpClient = httpClient,
    timeService = timeService,
    tokenEndpointUrl = tokenEndpointUrl,
    clientId = clientId,
    clientSecret = clientSecret,
    scope = scope,
    callerName = "aml-service",
)
