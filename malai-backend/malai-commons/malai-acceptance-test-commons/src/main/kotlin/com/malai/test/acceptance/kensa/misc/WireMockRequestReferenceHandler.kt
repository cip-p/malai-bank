package com.malai.test.acceptance.kensa.misc

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.ServeEvent

class WireMockRequestReferenceHandler : ResponseTransformerV2 {

    override fun transform(response: Response, serveEvent: ServeEvent): Response {
        if (!serveEvent.request.containsHeader(HEADER_NAME)) return response

        return Response.Builder.like(response)
            .but()
            .headers(response.headers.plus(serveEvent.request.header(HEADER_NAME)))
            .build()
    }

    override fun getName(): String = WireMockRequestReferenceHandler::class.simpleName!!

    private companion object {
        private const val HEADER_NAME = "Request-Reference"
    }
}
