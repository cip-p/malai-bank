package com.malai.cognitostub

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

class OAuth2TokenTransformer(private val tokens: TokenMinter) : ResponseDefinitionTransformerV2 {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName() = NAME

    override fun applyGlobally() = false

    override fun transform(serveEvent: ServeEvent): ResponseDefinition {
        val form = formFields(serveEvent.request.bodyAsString)
        val clientId = basicAuthClientId(AwsJson.header(serveEvent.request, "Authorization")) ?: form["client_id"]
            ?: return AwsJson.json(serveEvent, 401, mapOf("error" to "invalid_client"), JSON)
        val scope = form["scope"].orEmpty()
        log.info("issued service token for client {} with scope '{}'", clientId, scope)
        return AwsJson.json(
            serveEvent,
            200,
            mapOf(
                "access_token" to tokens.serviceAccessToken(clientId, scope),
                "expires_in" to TokenMinter.ACCESS_TOKEN_TTL.seconds,
                "token_type" to "Bearer",
            ),
            JSON,
        )
    }

    private fun basicAuthClientId(authorization: String?): String? =
        authorization
            ?.takeIf { it.startsWith("Basic ", ignoreCase = true) }
            ?.substring("Basic ".length)
            ?.let { runCatching { String(Base64.getDecoder().decode(it), UTF_8) }.getOrNull() }
            ?.substringBefore(':')
            ?.takeUnless { it.isBlank() }

    private fun formFields(body: String?): Map<String, String> =
        body.orEmpty()
            .split('&')
            .mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (key, value) ->
                    URLDecoder.decode(key, UTF_8) to URLDecoder.decode(value, UTF_8)
                }
            }
            .toMap()

    companion object {
        const val NAME = "cognito-oauth2"
        private const val JSON = "application/json"
    }
}
