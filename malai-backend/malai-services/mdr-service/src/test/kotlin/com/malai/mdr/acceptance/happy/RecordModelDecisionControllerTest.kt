package com.malai.mdr.acceptance.happy

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.mdr.support.PersistedMdrEntry
import com.malai.client.malai.mdr.builders.request.RecordModelDecisionRequestBuilder.Companion.aDefaultRecordModelDecisionRequest
import com.malai.client.malai.mdr.builders.request.RecordModelDecisionRequestBuilder.Companion.aTimedOutRecordModelDecisionRequest
import com.malai.client.malai.mdr.builders.request.RecordModelDecisionRequestBuilder.Companion.anErroredRecordModelDecisionRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import java.time.Instant
import java.util.UUID

class RecordModelDecisionControllerTest : MdrAcceptanceTest() {

    private val someEntryId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val someDecisionId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    private val completedEntryId = UUID.fromString("c0000000-0000-0000-0000-000000000001")
    private val completedDecisionId = UUID.fromString("c0000000-0000-0000-0000-000000000002")
    private val timedOutEntryId = UUID.fromString("c0000000-0000-0000-0000-000000000003")
    private val timedOutDecisionId = UUID.fromString("c0000000-0000-0000-0000-000000000004")
    private val erroredEntryId = UUID.fromString("c0000000-0000-0000-0000-000000000005")
    private val erroredDecisionId = UUID.fromString("c0000000-0000-0000-0000-000000000006")

    @Test
    fun recordCompletedModelDecision() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        aDefaultRecordModelDecisionRequest()
                            .withEntryId(someEntryId)
                            .withDecisionId(someDecisionId),
                    )
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "seq" To 1
                "outcome" To "COMPLETED"
                noExtraProps()
            }
        }

        and(theMdrEntryInTheDatabaseIs(someEntryId)) {
            this shouldBe PersistedMdrEntry(
                entryId = someEntryId,
                ts = Instant.parse("2026-10-15T10:30:00Z"),
                entryKind = "MODEL_DECISION",
                decisionId = someDecisionId,
                parentEntryId = null,
                correlationType = "PAYMENT",
                correlationId = "pmt-abc-123",
                advisory = false,
                modelId = "claude-opus-4-8",
                modelVersion = "2026-01",
                promptTemplateId = "fraud-triage",
                promptTemplateVersion = "v3",
                promptTemplateHashHex = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                renderedPrompt = "Assess payment pmt-abc-123 for fraud risk.",
                retrievedContext = listOf(mapOf("type" to "CASE", "id" to "case-42", "contentHash" to "abc123")),
                outcome = "COMPLETED",
                rawOutput = "APPROVE: low risk.",
                verdict = mapOf("decision" to "APPROVE", "rationale" to "no anomalies"),
                errorDetail = null,
                latencyMs = 842L,
                costMicros = 1500L,
                costCurrency = "GBP",
                humanId = null,
                disposition = null,
                editedOutput = null,
                seq = 1L,
            )
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1
            (this[0]["seq"] as Number).toLong() shouldBe 1L
            (this[0]["prev_hash"] as ByteArray) shouldBe ByteArray(32)
            toHex(this[0]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[0])
        }

        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 1L
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "MDR_DECISION_RECORDED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["resource_type"] shouldBe "ModelDecision"
            this[0]["resource_id"] shouldBe someDecisionId.toString()
        }
    }

    @Test
    fun recordAdvisoryModelDecisionWithTheFlagHashCoveredOnTheEntry() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        aDefaultRecordModelDecisionRequest()
                            .withEntryId(someEntryId)
                            .withDecisionId(someDecisionId)
                            .withAdvisory(true),
                    )
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "seq" To 1
                "outcome" To "COMPLETED"
                noExtraProps()
            }
        }

        and(theMdrEntryInTheDatabaseIs(someEntryId)) {
            this shouldBe PersistedMdrEntry(
                entryId = someEntryId,
                ts = Instant.parse("2026-10-15T10:30:00Z"),
                entryKind = "MODEL_DECISION",
                decisionId = someDecisionId,
                parentEntryId = null,
                correlationType = "PAYMENT",
                correlationId = "pmt-abc-123",
                advisory = true,
                modelId = "claude-opus-4-8",
                modelVersion = "2026-01",
                promptTemplateId = "fraud-triage",
                promptTemplateVersion = "v3",
                promptTemplateHashHex = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                renderedPrompt = "Assess payment pmt-abc-123 for fraud risk.",
                retrievedContext = listOf(mapOf("type" to "CASE", "id" to "case-42", "contentHash" to "abc123")),
                outcome = "COMPLETED",
                rawOutput = "APPROVE: low risk.",
                verdict = mapOf("decision" to "APPROVE", "rationale" to "no anomalies"),
                errorDetail = null,
                latencyMs = 842L,
                costMicros = 1500L,
                costCurrency = "GBP",
                humanId = null,
                disposition = null,
                editedOutput = null,
                seq = 1L,
            )
        }

        and(theMdrEntriesInTheDatabase()) {
            toHex(this[0]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[0])
            toHex(this[0]["current_hash"] as ByteArray) shouldNotBe
                recomputedCurrentHashHex(this[0] + mapOf("advisory" to false))
        }
    }

    @Test
    fun redactsSensitiveValuesBeforeHashingAndPersisting() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        aDefaultRecordModelDecisionRequest()
                            .withEntryId(someEntryId)
                            .withRenderedPrompt("Charge card 9123456789012345 now")
                            .withRawOutput("Declined for card 9123456789012345")
                            .withVerdict(mapOf("decision" to "DECLINE", "note" to "card 9123456789012345 flagged")),
                    )
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(theMdrEntryInTheDatabaseIs(someEntryId)) {
            renderedPrompt shouldBe "Charge card [REDACTED] now"
            rawOutput shouldBe "Declined for card [REDACTED]"
            verdict shouldBe mapOf("decision" to "DECLINE", "note" to "card [REDACTED] flagged")
        }

        and(theMdrEntriesInTheDatabase()) {
            toHex(this[0]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[0])
        }
    }

    @Test
    fun replayingWithTheSameEntryIdReturnsTheExistingEntryWithoutADuplicateAppend() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(aDefaultRecordModelDecisionRequest().withEntryId(someEntryId).withDecisionId(someDecisionId))
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(aDefaultRecordModelDecisionRequest().withEntryId(someEntryId).withDecisionId(someDecisionId))
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "seq" To 1
                "outcome" To "COMPLETED"
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(theNumberOfAuditEventsWithAction("MDR_DECISION_RECORDED")) {
            this shouldBe 1L
        }
    }

    @Test
    fun recordTimedOutModelDecisionWithNoRawOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        aTimedOutRecordModelDecisionRequest()
                            .withEntryId(someEntryId)
                            .withDecisionId(someDecisionId),
                    )
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "seq" To 1
                "outcome" To "TIMED_OUT"
                noExtraProps()
            }
        }

        and(theMdrEntryInTheDatabaseIs(someEntryId)) {
            this shouldBe PersistedMdrEntry(
                entryId = someEntryId,
                ts = Instant.parse("2026-10-15T10:30:00Z"),
                entryKind = "MODEL_DECISION",
                decisionId = someDecisionId,
                parentEntryId = null,
                correlationType = "PAYMENT",
                correlationId = "pmt-abc-123",
                advisory = false,
                modelId = "claude-opus-4-8",
                modelVersion = "2026-01",
                promptTemplateId = "fraud-triage",
                promptTemplateVersion = "v3",
                promptTemplateHashHex = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                renderedPrompt = "Assess payment pmt-abc-123 for fraud risk.",
                retrievedContext = listOf(mapOf("type" to "CASE", "id" to "case-42", "contentHash" to "abc123")),
                outcome = "TIMED_OUT",
                rawOutput = null,
                verdict = null,
                errorDetail = null,
                latencyMs = 842L,
                costMicros = 1500L,
                costCurrency = "GBP",
                humanId = null,
                disposition = null,
                editedOutput = null,
                seq = 1L,
            )
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1
            (this[0]["prev_hash"] as ByteArray) shouldBe ByteArray(32)
            toHex(this[0]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[0])
        }

        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 1L
        }

        and(theNumberOfAuditEventsWithAction("MDR_DECISION_RECORDED")) {
            this shouldBe 1L
        }
    }

    @Test
    fun recordErroredModelDecisionWithErrorDetail() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        anErroredRecordModelDecisionRequest()
                            .withEntryId(someEntryId)
                            .withDecisionId(someDecisionId),
                    )
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To someEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "seq" To 1
                "outcome" To "ERRORED"
                noExtraProps()
            }
        }

        and(theMdrEntryInTheDatabaseIs(someEntryId)) {
            this shouldBe PersistedMdrEntry(
                entryId = someEntryId,
                ts = Instant.parse("2026-10-15T10:30:00Z"),
                entryKind = "MODEL_DECISION",
                decisionId = someDecisionId,
                parentEntryId = null,
                correlationType = "PAYMENT",
                correlationId = "pmt-abc-123",
                advisory = false,
                modelId = "claude-opus-4-8",
                modelVersion = "2026-01",
                promptTemplateId = "fraud-triage",
                promptTemplateVersion = "v3",
                promptTemplateHashHex = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                renderedPrompt = "Assess payment pmt-abc-123 for fraud risk.",
                retrievedContext = listOf(mapOf("type" to "CASE", "id" to "case-42", "contentHash" to "abc123")),
                outcome = "ERRORED",
                rawOutput = null,
                verdict = null,
                errorDetail = "Downstream returned HTTP 503 after 3 retries",
                latencyMs = 842L,
                costMicros = 1500L,
                costCurrency = "GBP",
                humanId = null,
                disposition = null,
                editedOutput = null,
                seq = 1L,
            )
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1
            toHex(this[0]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[0])
        }

        and(theNumberOfAuditEventsWithAction("MDR_DECISION_RECORDED")) {
            this shouldBe 1L
        }
    }

    @Test
    fun aMixedOutcomeSequenceChainsInSeqOrder() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        aDefaultRecordModelDecisionRequest()
                            .withEntryId(completedEntryId)
                            .withDecisionId(completedDecisionId),
                    )
                    .withServiceJwtToken(),
            ),
        )

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        aTimedOutRecordModelDecisionRequest()
                            .withEntryId(timedOutEntryId)
                            .withDecisionId(timedOutDecisionId),
                    )
                    .withServiceJwtToken(),
            ),
        )

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(
                        anErroredRecordModelDecisionRequest()
                            .withEntryId(erroredEntryId)
                            .withDecisionId(erroredDecisionId),
                    )
                    .withServiceJwtToken(),
            ),
        )

        then(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 3
            (this[0]["outcome"] as String) shouldBe "COMPLETED"
            (this[1]["outcome"] as String) shouldBe "TIMED_OUT"
            (this[2]["outcome"] as String) shouldBe "ERRORED"

            (this[0]["seq"] as Number).toLong() shouldBe 1L
            (this[1]["seq"] as Number).toLong() shouldBe 2L
            (this[2]["seq"] as Number).toLong() shouldBe 3L

            (this[0]["prev_hash"] as ByteArray) shouldBe ByteArray(32)
            toHex(this[0]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[0])
            toHex(this[1]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[1])
            toHex(this[2]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[2])

            (this[1]["prev_hash"] as ByteArray) shouldBe (this[0]["current_hash"] as ByteArray)
            (this[2]["prev_hash"] as ByteArray) shouldBe (this[1]["current_hash"] as ByteArray)
        }

        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 3L
        }
    }
}
