package com.malai.copilot.service

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.Usage
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import com.malai.copilot.model.SourceRef
import com.malai.copilot.model.Timeline
import org.slf4j.LoggerFactory
import java.io.InterruptedIOException
import java.time.Duration

fun anthropicClientWithin(properties: AnthropicDeciderProperties, budget: Duration): AnthropicClient {
    val apiKey = properties.apiKey?.takeIf { it.isNotBlank() }
        ?: error("malai.copilot.anthropic.api-key must be set when malai.copilot.decider is anthropic")

    return AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .maxRetries(properties.maxRetries)
        .timeout(budget)
        .apply { properties.baseUrl?.let { baseUrl(it) } }
        .build()
}

class AnthropicCopilotDecider(
    private val client: AnthropicClient,
    private val properties: AnthropicDeciderProperties,
) : CopilotDecider {

    private val log = LoggerFactory.getLogger(javaClass)

    private val outputMapper = jacksonObjectMapper()

    override val modelId: String = properties.model

    override val modelVersion: String = properties.model

    override val costCurrency: String = properties.costCurrency

    override fun explain(timeline: Timeline, renderedPrompt: String): DeciderOutcome {
        val message = try {
            client.messages().create(requestCarrying(renderedPrompt))
        } catch (ex: AnthropicIoException) {
            throw ex.asTimeoutOrRethrow()
        }

        val resolvedModel = message.model().asString()
        val costMicros = costOf(message.usage())
        log.info(
            "copilot provider answered: subject={} model={} inputTokens={} outputTokens={} costMicros={} currency={}",
            timeline.subjectId, resolvedModel, message.usage().inputTokens(), message.usage().outputTokens(),
            costMicros, properties.costCurrency,
        )

        return DeciderOutcome(
            claims = message.claims(),
            modelVersion = resolvedModel,
            costMicros = costMicros,
        )
    }

    private fun requestCarrying(renderedPrompt: String): MessageCreateParams = MessageCreateParams.builder()
        .model(properties.model)
        .maxTokens(properties.maxTokens)
        .thinking(ThinkingConfigAdaptive.builder().build())
        .outputConfig(
            OutputConfig.builder()
                .effort(OutputConfig.Effort.of(properties.effort))
                .format(JsonOutputFormat.builder().schema(CLAIMS_SCHEMA).build())
                .build(),
        )
        .addUserMessage(renderedPrompt)
        .build()

    private fun Message.claims(): List<Claim> {
        when (val stopReason = stopReason().map { it.asString() }.orElse(NO_STOP_REASON)) {
            REFUSAL -> throw IllegalStateException("the provider declined to answer: ${refusalDetail()}")
            MAX_TOKENS -> throw IllegalStateException(
                "the provider was cut off at ${properties.maxTokens} output tokens before it finished its answer",
            )
            else -> log.debug("copilot provider stop reason: {}", stopReason)
        }

        return verdictWithin(text()).claims.map { claim ->
            Claim(text = claim.text, sources = claim.sources.map { SourceRef(it.type, it.id) })
        }
    }

    private fun Message.refusalDetail(): String = stopDetails()
        .map {
            val category = it.category().map { category -> category.asString() }.orElse("uncategorised")
            "$category: ${it.explanation().orElse("no explanation given")}"
        }
        .orElse("no detail given")

    private fun Message.text(): String = content()
        .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
        .joinToString(separator = "")

    private fun verdictWithin(rawOutput: String): VerdictJson = try {
        outputMapper.readValue<VerdictJson>(rawOutput)
    } catch (ex: Exception) {
        throw IllegalStateException("the provider produced an answer the platform could not read: ${ex.message}", ex)
    }

    private fun costOf(usage: Usage): Long {
        val weighted = usage.inputTokens() * properties.inputMicrosPerMillionTokens +
            usage.outputTokens() * properties.outputMicrosPerMillionTokens +
            usage.cacheCreationInputTokens().orElse(0L) * properties.cacheWriteMicrosPerMillionTokens +
            usage.cacheReadInputTokens().orElse(0L) * properties.cacheReadMicrosPerMillionTokens
        return Math.ceilDiv(weighted, TOKENS_PER_MILLION)
    }

    private fun AnthropicIoException.asTimeoutOrRethrow(): RuntimeException =
        if (generateSequence(cause) { it.cause }.any { it is InterruptedIOException }) {
            CopilotTimedOutException("the provider did not answer within the adapter's read timeout")
        } else {
            this
        }

    private data class VerdictJson(val claims: List<ClaimJson>)

    private data class ClaimJson(val text: String, val sources: List<SourceRefJson>)

    private data class SourceRefJson(val type: String, val id: String)

    private companion object {
        const val NO_STOP_REASON = "none"
        const val REFUSAL = "refusal"
        const val MAX_TOKENS = "max_tokens"
        const val TOKENS_PER_MILLION = 1_000_000L

        val CLAIMS_SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "claims" to mapOf(
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
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("claims")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
