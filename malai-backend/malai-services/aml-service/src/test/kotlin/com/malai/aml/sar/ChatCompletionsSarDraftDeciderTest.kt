package com.malai.aml.sar

import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
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

private const val COMPLETIONS_PATH = "/v1/chat/completions"
private const val SOME_API_KEY = "hf_not_a_real_key"
private const val SOME_RENDERED_PROMPT = "SAR TIMELINE\nPAYMENT 00000000-0000-4000-8000-0000000000b1 :: state=SETTLED"
private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000c1")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000b1")
private const val SOME_ALERT_DECISION_ID = "00000000-0000-4000-8000-0000000000d1"
private val SOME_MOMENT: Instant = Instant.parse("2026-07-01T12:00:00Z")
private val A_BUDGET: Duration = Duration.ofMillis(500)
private val LONGER_THAN_THE_BUDGET = A_BUDGET.toMillis().toInt() * 4

class ChatCompletionsSarDraftDeciderTest {

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
        theProviderAnswers(withStatements())

        deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT)

        val request = theRequestTheProviderReceived()
        request.path("messages").size() shouldBe 1
        request.path("messages").path(0).path("role").asString() shouldBe "user"
        request.path("messages").path(0).path("content").asString() shouldBe SOME_RENDERED_PROMPT
    }

    @Test
    fun theRequestNamesTheConfiguredModelItsOutputCeilingAndItsConstrainedShape() {
        theProviderAnswers(withStatements())

        deciderFor(someProperties(model = "llama3.1:8b", maxTokens = 2_048L))
            .draft(aTimeline(), SOME_RENDERED_PROMPT)

        val request = theRequestTheProviderReceived()
        request.path("model").asString() shouldBe "llama3.1:8b"
        request.path("max_tokens").asLong() shouldBe 2_048L
        request.path("response_format").path("type").asString() shouldBe "json_schema"
        request.path("response_format").path("json_schema").path("strict").asBoolean() shouldBe true
    }

    @Test
    fun theAnswersShapeIsConstrainedByTheAdapterSoNoShapeInstructionEntersThePrompt() {
        theProviderAnswers(withStatements())

        deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT)

        val statement = theRequestTheProviderReceived()
            .path("response_format").path("json_schema").path("schema")
            .path("properties").path("statements").path("items")

        statement.path("required").values().map { it.asString() } shouldContainExactly listOf("text", "sources")
        statement.path("additionalProperties").asBoolean() shouldBe false
    }

    @Test
    fun theCitedRecordTypeIsLeftUnconstrainedSoAFabricationReachesGroundingRatherThanTheSchema() {
        theProviderAnswers(withStatements())

        deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT)

        theRequestTheProviderReceived()
            .path("response_format").path("json_schema").path("schema")
            .path("properties").path("statements").path("items")
            .path("properties").path("sources").path("items")
            .path("properties").path("type")
            .has("enum") shouldBe false
    }

    @Test
    fun theKeyTravelsAsTheProvidersOwnHeaderAndNeverInsideThePrompt() {
        theProviderAnswers(withStatements())

        deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT)

        val received = wiremock.allServeEvents.single().request
        received.getHeader("Authorization") shouldBe "Bearer $SOME_API_KEY"
        received.bodyAsString shouldNotContain SOME_API_KEY
    }

    @Test
    fun aKeylessLocalProviderIsCalledWithNoAuthorizationHeaderAtAll() {
        theProviderAnswers(withStatements())

        deciderFor(someProperties(apiKey = null)).draft(aTimeline(), SOME_RENDERED_PROMPT)

        wiremock.allServeEvents.single().request.containsHeader("Authorization") shouldBe false
    }

    @Test
    fun theProvidersStatementsBecomeStatementsCarryingTheCitationsItGave() {
        theProviderAnswers(
            withStatements(
                """{"text":"The account settled a payment the bank considers suspicious.","sources":[{"type":"PAYMENT","id":"$SOME_PAYMENT_ID"}]}""",
                """{"text":"Transaction monitoring raised an alert on the account.","sources":[{"type":"ALERT_DECISION","id":"$SOME_ALERT_DECISION_ID"}]}""",
            ),
        )

        val outcome = deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT)

        outcome.statements shouldContainExactly listOf(
            SarStatement(
                "The account settled a payment the bank considers suspicious.",
                listOf(SarRef("PAYMENT", SOME_PAYMENT_ID.toString())),
            ),
            SarStatement(
                "Transaction monitoring raised an alert on the account.",
                listOf(SarRef("ALERT_DECISION", SOME_ALERT_DECISION_ID)),
            ),
        )
    }

    @Test
    fun theRecordCarriesTheModelThatAnsweredRatherThanTheOneThatWasAsked() {
        theProviderAnswers(withStatements(), model = "qwen2.5:7b-instruct-q4_K_M")

        deciderFor(someProperties(model = "qwen2.5:7b-instruct"))
            .draft(aTimeline(), SOME_RENDERED_PROMPT).modelVersion shouldBe "qwen2.5:7b-instruct-q4_K_M"
    }

    @Test
    fun theCostIsDrawnFromTheTokensTheProviderBilled() {
        theProviderAnswers(withStatements(), promptTokens = 1_000, completionTokens = 200)

        deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT).costMicros shouldBe 18_000L
    }

    @Test
    fun aCostThatDoesNotDivideEvenlyRoundsUpSoTheRecordNeverUnderstatesTheBill() {
        theProviderAnswers(withStatements(), completionTokens = 1)

        deciderFor(someProperties(outputMicrosPerMillionTokens = 500_000L))
            .draft(aTimeline(), SOME_RENDERED_PROMPT).costMicros shouldBe 1L
    }

    @Test
    fun aFreeLocalProviderRecordsAnHonestZeroCost() {
        theProviderAnswers(withStatements(), promptTokens = 1_000, completionTokens = 200)

        deciderFor(someProperties(inputMicrosPerMillionTokens = 0L, outputMicrosPerMillionTokens = 0L))
            .draft(aTimeline(), SOME_RENDERED_PROMPT).costMicros shouldBe 0L
    }

    @Test
    fun aProviderRefusalIsAnErrorCarryingTheReasonTheProviderGave() {
        theProviderAnswers(null, refusal = "I cannot help with drafting this report.")

        val thrown = shouldThrow<IllegalStateException> { deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT) }

        thrown.message!! shouldContain "declined to answer: I cannot help with drafting this report."
    }

    @Test
    fun anAnswerCutOffAtTheOutputCeilingIsAnErrorRatherThanAHalfReadVerdict() {
        theProviderAnswers(withStatements(), finishReason = "length")

        val thrown = shouldThrow<IllegalStateException> {
            deciderFor(someProperties(maxTokens = 64L)).draft(aTimeline(), SOME_RENDERED_PROMPT)
        }

        thrown.message!! shouldContain "cut off at 64 output tokens"
    }

    @Test
    fun anAnswerThePlatformCannotReadIsAnErrorRatherThanAnEmptyNarrative() {
        theProviderAnswers("I would rather narrate this in prose.")

        val thrown = shouldThrow<IllegalStateException> { deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT) }

        thrown.message!! shouldContain "could not read"
    }

    @Test
    fun anAnswerMissingTheCitationsEveryStatementOwesIsAnErrorRatherThanAStatementCitingNothing() {
        theProviderAnswers(withStatements("""{"text":"The account settled a payment."}"""))

        shouldThrow<IllegalStateException> { deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT) }
    }

    @Test
    fun aProviderFailureSurfacesAsItselfRatherThanBeingReadAsAnEmptyAnswer() {
        wiremock.stubFor(post(urlPathEqualTo(COMPLETIONS_PATH)).willReturn(aResponse().withStatus(500)))

        val thrown = shouldThrow<IllegalStateException> { deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT) }

        thrown.message!! shouldContain "500"
    }

    @Test
    fun aProviderOutlivingTheAdaptersReadTimeoutKeepsTheSarTimeoutVocabulary() {
        wiremock.stubFor(
            post(urlPathEqualTo(COMPLETIONS_PATH)).willReturn(
                aResponse().withStatus(200).withFixedDelay(LONGER_THAN_THE_BUDGET).withBody(withStatements()),
            ),
        )

        shouldThrow<SarDraftTimedOutException> { deciderFor().draft(aTimeline(), SOME_RENDERED_PROMPT) }
    }

    private fun deciderFor(properties: ChatCompletionsDeciderProperties = someProperties()) =
        ChatCompletionsSarDraftDecider(properties, A_BUDGET)

    private fun someProperties(
        apiKey: String? = SOME_API_KEY,
        model: String = "qwen2.5:7b-instruct",
        maxTokens: Long = 4_096L,
        inputMicrosPerMillionTokens: Long = 10_000_000L,
        outputMicrosPerMillionTokens: Long = 40_000_000L,
    ) = ChatCompletionsDeciderProperties(
        baseUrl = "${wiremock.baseUrl()}/v1",
        apiKey = apiKey,
        model = model,
        maxTokens = maxTokens,
        inputMicrosPerMillionTokens = inputMicrosPerMillionTokens,
        outputMicrosPerMillionTokens = outputMicrosPerMillionTokens,
    )

    private fun theProviderAnswers(
        content: String?,
        model: String = "qwen2.5:7b-instruct",
        finishReason: String = "stop",
        refusal: String? = null,
        promptTokens: Long = 0,
        completionTokens: Long = 0,
    ) {
        wiremock.stubFor(
            post(urlPathEqualTo(COMPLETIONS_PATH)).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                    """
                    {
                      "id": "chatcmpl-01",
                      "object": "chat.completion",
                      "created": 1753574400,
                      "model": "$model",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": ${mapper.writeValueAsString(content)},
                            "refusal": ${mapper.writeValueAsString(refusal)}
                          },
                          "finish_reason": "$finishReason"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": $promptTokens,
                        "completion_tokens": $completionTokens,
                        "total_tokens": ${promptTokens + completionTokens}
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )
    }

    private fun withStatements(vararg statements: String): String =
        """{"statements":[${statements.joinToString(",")}]}"""

    private fun theRequestTheProviderReceived(): JsonNode =
        mapper.readTree(wiremock.allServeEvents.single().request.bodyAsString)

    private fun aTimeline() = SarTimeline(
        caseId = SOME_CASE_ID,
        accountId = SOME_ACCOUNT_ID,
        openedAt = SOME_MOMENT,
        records = listOf(
            SarRecord(
                type = SarRecordType.PAYMENT,
                id = SOME_PAYMENT_ID.toString(),
                moment = SOME_MOMENT,
                summary = "state=SETTLED",
            ),
        ),
    )
}
