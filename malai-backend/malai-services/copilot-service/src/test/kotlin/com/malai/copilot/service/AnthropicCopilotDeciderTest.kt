package com.malai.copilot.service

import com.anthropic.errors.AnthropicServiceException
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.malai.copilot.model.SourceRecord
import com.malai.copilot.model.SourceRecordType
import com.malai.copilot.model.SourceRef
import com.malai.copilot.model.Timeline
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val MESSAGES_PATH = "/v1/messages"
private const val SOME_API_KEY = "sk-ant-not-a-real-key"
private const val SOME_RENDERED_PROMPT = "TIMELINE\nPAYMENT 00000000-0000-4000-8000-0000000000b1 :: state=SETTLED"
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000b1")
private const val SOME_LEDGER_ENTRY_ID = "00000000-0000-4000-8000-0000000000a1"
private val SOME_MOMENT: Instant = Instant.parse("2026-05-03T12:00:00Z")
private val A_BUDGET: Duration = Duration.ofMillis(500)
private val LONGER_THAN_THE_BUDGET = A_BUDGET.toMillis().toInt() * 4

class AnthropicCopilotDeciderTest {

    private val wiremock = WireMockServer(options().dynamicPort())

    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun startTheProvider() {
        wiremock.start()
    }

    @AfterEach
    fun stopTheProvider() {
        wiremock.stop()
    }

    @Test
    fun theRenderedPromptIsTheOnlySubjectMaterialTheAdapterSends() {
        theProviderAnswers(withClaims())

        deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT)

        val request = theRequestTheProviderReceived()
        request.path("messages").size() shouldBe 1
        request.path("messages").path(0).path("role").asString() shouldBe "user"
        request.userMessageText() shouldBe SOME_RENDERED_PROMPT
        request.has("system") shouldBe false
    }

    @Test
    fun theRequestNamesTheConfiguredModelItsOutputCeilingAndItsEffort() {
        theProviderAnswers(withClaims())

        deciderFor(someProperties(model = "claude-opus-4-8", maxTokens = 2_048L, effort = "medium"))
            .explain(aTimeline(), SOME_RENDERED_PROMPT)

        val request = theRequestTheProviderReceived()
        request.path("model").asString() shouldBe "claude-opus-4-8"
        request.path("max_tokens").asLong() shouldBe 2_048L
        request.path("output_config").path("effort").asString() shouldBe "medium"
        request.path("thinking").path("type").asString() shouldBe "adaptive"
    }

    @Test
    fun theAnswersShapeIsConstrainedByTheAdapterSoNoShapeInstructionEntersThePrompt() {
        theProviderAnswers(withClaims())

        deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT)

        val claim = theRequestTheProviderReceived()
            .path("output_config").path("format").path("schema")
            .path("properties").path("claims").path("items")

        claim.path("required").values().map { it.asString() } shouldContainExactly listOf("text", "sources")
        claim.path("additionalProperties").asBoolean() shouldBe false
    }

    @Test
    fun theCitedRecordTypeIsLeftUnconstrainedSoAFabricationReachesGroundingRatherThanTheSchema() {
        theProviderAnswers(withClaims())

        deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT)

        theRequestTheProviderReceived()
            .path("output_config").path("format").path("schema")
            .path("properties").path("claims").path("items")
            .path("properties").path("sources").path("items")
            .path("properties").path("type")
            .has("enum") shouldBe false
    }

    @Test
    fun theKeyTravelsAsTheProvidersOwnHeaderAndNeverInsideThePrompt() {
        theProviderAnswers(withClaims())

        deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT)

        val received = wiremock.allServeEvents.single().request
        received.getHeader("Authorization") shouldBe "Bearer $SOME_API_KEY"
        received.bodyAsString shouldNotContain SOME_API_KEY
    }

    @Test
    fun theProvidersClaimsBecomeClaimsCarryingTheCitationsItGave() {
        theProviderAnswers(
            withClaims(
                """{"text":"The payment settled.","sources":[{"type":"PAYMENT","id":"$SOME_PAYMENT_ID"}]}""",
                """{"text":"The ledger posted it.","sources":[{"type":"LEDGER_ENTRY","id":"$SOME_LEDGER_ENTRY_ID"}]}""",
            ),
        )

        val outcome = deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT)

        outcome.claims shouldContainExactly listOf(
            Claim("The payment settled.", listOf(SourceRef("PAYMENT", SOME_PAYMENT_ID.toString()))),
            Claim("The ledger posted it.", listOf(SourceRef("LEDGER_ENTRY", SOME_LEDGER_ENTRY_ID))),
        )
    }

    @Test
    fun theRecordCarriesTheModelThatAnsweredRatherThanTheOneThatWasAsked() {
        theProviderAnswers(withClaims(), model = "claude-opus-4-8-20260101")

        deciderFor(someProperties(model = "claude-opus-4-8"))
            .explain(aTimeline(), SOME_RENDERED_PROMPT).modelVersion shouldBe "claude-opus-4-8-20260101"
    }

    @Test
    fun theCostIsDrawnFromTheTokensTheProviderBilledIncludingItsCacheTraffic() {
        theProviderAnswers(withClaims(), inputTokens = 1_000, outputTokens = 200, cacheWrite = 400, cacheRead = 8_000)

        deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT).costMicros shouldBe 16_500L
    }

    @Test
    fun aCostThatDoesNotDivideEvenlyRoundsUpSoTheRecordNeverUnderstatesTheBill() {
        theProviderAnswers(withClaims(), cacheRead = 1)

        deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT).costMicros shouldBe 1L
    }

    @Test
    fun aProviderRefusalIsAnErrorCarryingTheReasonTheProviderGave() {
        theProviderAnswers(withClaims(), stopReason = "refusal", stopDetails = ""","stop_details":{"type":"refusal","category":"cyber","explanation":"declined by policy"}""")

        val thrown = shouldThrow<IllegalStateException> { deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT) }

        thrown.message!! shouldContain "cyber: declined by policy"
    }

    @Test
    fun anAnswerCutOffAtTheOutputCeilingIsAnErrorRatherThanAHalfReadVerdict() {
        theProviderAnswers(withClaims(), stopReason = "max_tokens")

        val thrown = shouldThrow<IllegalStateException> {
            deciderFor(someProperties(maxTokens = 64L)).explain(aTimeline(), SOME_RENDERED_PROMPT)
        }

        thrown.message!! shouldContain "cut off at 64 output tokens"
    }

    @Test
    fun anAnswerThePlatformCannotReadIsAnErrorRatherThanAnEmptyExplanation() {
        theProviderAnswers("I would rather explain this in prose.")

        val thrown = shouldThrow<IllegalStateException> { deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT) }

        thrown.message!! shouldContain "could not read"
    }

    @Test
    fun anAnswerMissingTheCitationsEveryClaimOwesIsAnErrorRatherThanAClaimCitingNothing() {
        theProviderAnswers(withClaims("""{"text":"The payment settled."}"""))

        shouldThrow<IllegalStateException> { deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT) }
    }

    @Test
    fun aProviderFailureSurfacesAsItselfRatherThanBeingReadAsAnEmptyAnswer() {
        wiremock.stubFor(post(urlPathEqualTo(MESSAGES_PATH)).willReturn(aResponse().withStatus(500)))

        shouldThrow<AnthropicServiceException> { deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT) }
    }

    @Test
    fun aProviderOutlivingTheAdaptersReadTimeoutKeepsTheCopilotsTimeoutVocabulary() {
        wiremock.stubFor(
            post(urlPathEqualTo(MESSAGES_PATH)).willReturn(
                aResponse().withStatus(200).withFixedDelay(LONGER_THAN_THE_BUDGET).withBody(withClaims()),
            ),
        )

        shouldThrow<CopilotTimedOutException> { deciderFor().explain(aTimeline(), SOME_RENDERED_PROMPT) }
    }

    private fun deciderFor(properties: AnthropicDeciderProperties = someProperties()) =
        AnthropicCopilotDecider(anthropicClientWithin(properties, A_BUDGET), properties)

    private fun someProperties(
        model: String = "claude-opus-4-8",
        maxTokens: Long = 4_096L,
        effort: String = "low",
    ) = AnthropicDeciderProperties(
        apiKey = SOME_API_KEY,
        baseUrl = wiremock.baseUrl(),
        model = model,
        effort = effort,
        maxTokens = maxTokens,
    )

    private fun theProviderAnswers(
        text: String,
        model: String = "claude-opus-4-8",
        stopReason: String = "end_turn",
        stopDetails: String = "",
        inputTokens: Long = 0,
        outputTokens: Long = 0,
        cacheWrite: Long = 0,
        cacheRead: Long = 0,
    ) {
        wiremock.stubFor(
            post(urlPathEqualTo(MESSAGES_PATH)).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                    """
                    {
                      "id": "msg_01",
                      "type": "message",
                      "role": "assistant",
                      "model": "$model",
                      "content": [{"type": "text", "text": ${mapper.writeValueAsString(text)}}],
                      "stop_reason": "$stopReason"$stopDetails,
                      "usage": {
                        "input_tokens": $inputTokens,
                        "output_tokens": $outputTokens,
                        "cache_creation_input_tokens": $cacheWrite,
                        "cache_read_input_tokens": $cacheRead
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )
    }

    private fun withClaims(vararg claims: String): String = """{"claims":[${claims.joinToString(",")}]}"""

    private fun theRequestTheProviderReceived(): JsonNode =
        mapper.readTree(wiremock.allServeEvents.single().request.bodyAsString)

    private fun JsonNode.userMessageText(): String {
        val content = path("messages").path(0).path("content")
        return if (content.isString) content.asString() else content.joinToString("") { it.path("text").asString() }
    }

    private fun aTimeline() = Timeline(
        subjectType = "PAYMENT",
        subjectId = SOME_PAYMENT_ID,
        records = listOf(
            SourceRecord(
                type = SourceRecordType.PAYMENT,
                id = SOME_PAYMENT_ID.toString(),
                moment = SOME_MOMENT,
                summary = "state=SETTLED",
            ),
        ),
    )
}
