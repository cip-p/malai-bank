package com.malai.aml.sar

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import tools.jackson.module.kotlin.jacksonMapperBuilder

fun chatCompletionsEndpointWithin(properties: ChatCompletionsDeciderProperties): URI {
    val baseUrl = properties.baseUrl?.takeIf { it.isNotBlank() }
        ?: error("malai.aml.sar.chat-completions.base-url must be set when malai.aml.sar.decider is chat-completions")
    return URI.create("${baseUrl.trimEnd('/')}/chat/completions")
}

class ChatCompletionsSarDraftDecider(
    private val properties: ChatCompletionsDeciderProperties,
    private val budget: Duration,
) : SarDraftDecider {

    private val log = LoggerFactory.getLogger(javaClass)

    private val endpoint: URI = chatCompletionsEndpointWithin(properties)

    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(budget).build()

    private val envelopeMapper = jacksonMapperBuilder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    private val outputMapper = jacksonObjectMapper()

    override val modelId: String = properties.model

    override val modelVersion: String = properties.model

    override val costCurrency: String = properties.costCurrency

    override fun draft(timeline: SarTimeline, renderedPrompt: String): SarDraftOutcome {
        val answer = try {
            client.send(requestCarrying(renderedPrompt), HttpResponse.BodyHandlers.ofString())
        } catch (ex: HttpTimeoutException) {
            throw SarDraftTimedOutException("the provider did not answer within the adapter's read timeout")
        }

        check(answer.statusCode() == 200) { "the provider answered ${answer.statusCode()}: ${answer.body()}" }

        val completion = envelopeWithin(answer.body())
        val resolvedModel = completion.model ?: properties.model
        val costMicros = costOf(completion.usage)
        log.info(
            "sar provider answered: caseId={} model={} promptTokens={} completionTokens={} costMicros={} currency={}",
            timeline.caseId, resolvedModel, completion.usage?.promptTokens, completion.usage?.completionTokens,
            costMicros, properties.costCurrency,
        )

        return SarDraftOutcome(
            statements = completion.statements(),
            modelVersion = resolvedModel,
            costMicros = costMicros,
        )
    }

    private fun requestCarrying(renderedPrompt: String): HttpRequest {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(budget)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(outputMapper.writeValueAsString(bodyCarrying(renderedPrompt))))
        properties.apiKey?.takeIf { it.isNotBlank() }?.let { request.header("Authorization", "Bearer $it") }
        return request.build()
    }

    private fun bodyCarrying(renderedPrompt: String): Map<String, Any> = mapOf(
        "model" to properties.model,
        "max_tokens" to properties.maxTokens,
        "messages" to listOf(mapOf("role" to "user", "content" to renderedPrompt)),
        "response_format" to mapOf(
            "type" to "json_schema",
            "json_schema" to mapOf(
                "name" to "sar_draft",
                "strict" to true,
                "schema" to STATEMENTS_SCHEMA,
            ),
        ),
    )

    private fun envelopeWithin(rawBody: String): CompletionJson = try {
        envelopeMapper.readValue<CompletionJson>(rawBody)
    } catch (ex: Exception) {
        throw IllegalStateException("the provider answered in a shape the platform could not read: ${ex.message}", ex)
    }

    private fun CompletionJson.statements(): List<SarStatement> {
        val choice = choices.firstOrNull()
            ?: throw IllegalStateException("the provider answered with no choices")

        choice.message.refusal?.takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException("the provider declined to answer: $it")
        }

        if (choice.finishReason == LENGTH) {
            throw IllegalStateException(
                "the provider was cut off at ${properties.maxTokens} output tokens before it finished its answer",
            )
        }

        val content = choice.message.content?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("the provider answered with no narrative content")

        return verdictWithin(content).statements.map { statement ->
            SarStatement(text = statement.text, sources = statement.sources.map { SarRef(it.type, it.id) })
        }
    }

    private fun verdictWithin(content: String): VerdictJson = try {
        outputMapper.readValue<VerdictJson>(content)
    } catch (ex: Exception) {
        throw IllegalStateException("the provider produced an answer the platform could not read: ${ex.message}", ex)
    }

    private fun costOf(usage: UsageJson?): Long {
        val billed = usage ?: return 0L
        val weighted = billed.promptTokens * properties.inputMicrosPerMillionTokens +
            billed.completionTokens * properties.outputMicrosPerMillionTokens
        return Math.ceilDiv(weighted, TOKENS_PER_MILLION)
    }

    private data class CompletionJson(
        val model: String? = null,
        val choices: List<ChoiceJson> = emptyList(),
        val usage: UsageJson? = null,
    )

    private data class ChoiceJson(val message: ChoiceMessageJson, val finishReason: String? = null)

    private data class ChoiceMessageJson(val content: String? = null, val refusal: String? = null)

    private data class UsageJson(val promptTokens: Long = 0L, val completionTokens: Long = 0L)

    private data class VerdictJson(val statements: List<StatementJson>)

    private data class StatementJson(val text: String, val sources: List<SourceRefJson>)

    private data class SourceRefJson(val type: String, val id: String)

    private companion object {
        const val LENGTH = "length"
        const val TOKENS_PER_MILLION = 1_000_000L

        val STATEMENTS_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "statements" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "text" to mapOf("type" to "string"),
                            "sources" to mapOf(
                                "type" to "array",
                                "items" to mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "type" to mapOf("type" to "string"),
                                        "id" to mapOf("type" to "string"),
                                    ),
                                    "required" to listOf("type", "id"),
                                    "additionalProperties" to false,
                                ),
                            ),
                        ),
                        "required" to listOf("text", "sources"),
                        "additionalProperties" to false,
                    ),
                ),
            ),
            "required" to listOf("statements"),
            "additionalProperties" to false,
        )
    }
}
