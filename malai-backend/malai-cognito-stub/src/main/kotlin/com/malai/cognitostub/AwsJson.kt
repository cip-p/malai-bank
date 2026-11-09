package com.malai.cognitostub

import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent

object AwsJson {

    const val TARGET_HEADER = "X-Amz-Target"
    private const val CONTENT_TYPE = "application/x-amz-json-1.1"
    private val objectMapper = jacksonObjectMapper()

    fun parse(body: String?): JsonNode = objectMapper.readTree(body?.takeUnless { it.isBlank() } ?: "{}")

    fun ok(serveEvent: ServeEvent, body: Any): ResponseDefinition = json(serveEvent, 200, body)

    fun error(serveEvent: ServeEvent, error: CognitoError): ResponseDefinition =
        ResponseDefinitionBuilder.like(serveEvent.responseDefinition).but()
            .withStatus(400)
            .withHeader("Content-Type", CONTENT_TYPE)
            .withHeader("x-amzn-ErrorType", error.type)
            .withBody(objectMapper.writeValueAsString(mapOf("__type" to error.type, "message" to error.message)))
            .build()

    fun json(serveEvent: ServeEvent, status: Int, body: Any, contentType: String = CONTENT_TYPE): ResponseDefinition =
        ResponseDefinitionBuilder.like(serveEvent.responseDefinition).but()
            .withStatus(status)
            .withHeader("Content-Type", contentType)
            .withBody(objectMapper.writeValueAsString(body))
            .build()

    fun header(request: Request, name: String): String? =
        request.headers.getHeader(name).takeIf { it.isPresent }?.firstValue()
}

internal fun JsonNode.text(field: String): String? = get(field)?.takeUnless { it.isNull }?.asString()

internal fun JsonNode.username(): String = text("Username") ?: throw CognitoError.invalidParameter("Username is required")

internal fun JsonNode.clientId(): String = text("ClientId") ?: "local-client-id"

internal fun JsonNode.groupName(): String = text("GroupName") ?: throw CognitoError.invalidParameter("GroupName is required")

internal fun JsonNode.stringMap(field: String): Map<String, String> =
    get(field)?.properties()?.associate { it.key to it.value.asString() } ?: emptyMap()

internal fun JsonNode.userAttributes(): Map<String, String> =
    get("UserAttributes")?.associate { (it.text("Name") ?: "") to (it.text("Value") ?: "") } ?: emptyMap()

internal fun Map<String, String>.username(): String =
    this["USERNAME"] ?: throw CognitoError.invalidParameter("USERNAME is required")
