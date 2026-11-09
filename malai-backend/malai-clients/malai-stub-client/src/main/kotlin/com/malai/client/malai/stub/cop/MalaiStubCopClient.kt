package com.malai.client.malai.stub.cop

import com.malai.client.malai.AbstractMalaiClient
import com.malai.client.malai.stub.cop.model.StubCopLookupRequest
import com.malai.client.malai.stub.cop.model.StubCopLookupResponse
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MalaiStubCopClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.stub.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun lookup(request: StubCopLookupRequest): StubCopLookupResponse {
        val httpRequest = HttpPost("$malaiBaseUrl/cop/lookup")
        httpRequest.addHeader("Accept", "application/json")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))

        val responseBody = makeRequest(httpRequest, 200)
            ?: throw RuntimeException("expected a response body from POST /cop/lookup")
        return fromJson(responseBody, StubCopLookupResponse::class.java)
    }
}
