package com.malai.cognitostub

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import org.slf4j.LoggerFactory

class StubControlTransformer(private val users: UserDirectory) : ResponseDefinitionTransformerV2 {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName() = NAME

    override fun applyGlobally() = false

    override fun transform(serveEvent: ServeEvent): ResponseDefinition {
        if (serveEvent.request.method == RequestMethod.POST) {
            users.clear()
            log.info("reset: all users forgotten")
            return AwsJson.json(serveEvent, 200, mapOf("users" to 0), JSON)
        }
        return AwsJson.json(serveEvent, 200, users.all(), JSON)
    }

    companion object {
        const val NAME = "cognito-stub-control"
        private const val JSON = "application/json"
    }
}
