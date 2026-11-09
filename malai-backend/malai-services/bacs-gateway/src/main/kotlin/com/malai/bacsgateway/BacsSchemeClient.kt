package com.malai.bacsgateway

import com.malai.client.malai.AbstractMalaiClient
import com.malai.scheme.contracts.bacs.BacsSchemeCollectionReturn
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.StringEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BacsSchemeClient(
    malaiHttpClient: CloseableHttpClient,
    @Value("\${malai.bacs-scheme.base-url}") malaiBaseUrl: String,
) : AbstractMalaiClient(malaiHttpClient, malaiBaseUrl) {

    fun returnCollection(request: BacsSchemeCollectionReturn) {
        val httpRequest = HttpPost("$malaiBaseUrl/bacs/collections/returns")
        httpRequest.addHeader("Content-Type", "application/json")
        httpRequest.entity = StringEntity(toJson(request))
        makeRequest(httpRequest, 202)
    }
}
