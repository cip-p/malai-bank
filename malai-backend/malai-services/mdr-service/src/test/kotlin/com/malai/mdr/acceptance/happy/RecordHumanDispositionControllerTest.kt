package com.malai.mdr.acceptance.happy

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.mdr.support.PersistedMdrEntry
import com.malai.mdr.support.builders.RecordHumanDispositionRequestBuilder.Companion.anAcceptDispositionRequest
import com.malai.mdr.support.builders.RecordHumanDispositionRequestBuilder.Companion.anEditDispositionRequest
import com.malai.mdr.support.builders.RecordHumanDispositionRequestBuilder.Companion.aRejectDispositionRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import java.time.Instant
import java.util.UUID

class RecordHumanDispositionControllerTest : MdrAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val decisionEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000010")
    private val someDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000010")
    private val acceptEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000001")
    private val rejectEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000002")
    private val editEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000003")

    @Test
    fun adminAppendsAnAcceptDispositionChainedAfterTheDecision() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anAcceptDispositionRequest().withEntryId(acceptEntryId))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To acceptEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "parentEntryId" To decisionEntryId.toString()
                "seq" To 2
                "disposition" To "ACCEPT"
                noExtraProps()
            }
        }

        and(theMdrEntryInTheDatabaseIs(acceptEntryId)) {
            this shouldBe PersistedMdrEntry(
                entryId = acceptEntryId,
                ts = Instant.parse("2026-10-15T10:30:00Z"),
                entryKind = "HUMAN_DISPOSITION",
                decisionId = someDecisionId,
                parentEntryId = decisionEntryId,
                correlationType = "PAYMENT",
                correlationId = "pmt-verify",
                advisory = null,
                modelId = null,
                modelVersion = null,
                promptTemplateId = null,
                promptTemplateVersion = null,
                promptTemplateHashHex = null,
                renderedPrompt = null,
                retrievedContext = null,
                outcome = null,
                rawOutput = null,
                verdict = null,
                errorDetail = null,
                latencyMs = null,
                costMicros = null,
                costCurrency = null,
                humanId = someAdminUserId,
                disposition = "ACCEPT",
                editedOutput = null,
                seq = 2L,
            )
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 2
            (this[1]["seq"] as Number).toLong() shouldBe 2L
            (this[1]["prev_hash"] as ByteArray) shouldBe (this[0]["current_hash"] as ByteArray)
            toHex(this[1]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[1])
        }

        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 2L
        }

        and(theLastAuditEventWithAction("MDR_DISPOSITION_RECORDED")) {
            this["actor_type"] shouldBe "ADMIN"
            this["actor_id"].toString() shouldBe someAdminUserId.toString()
            this["resource_type"] shouldBe "ModelDecision"
            this["resource_id"] shouldBe someDecisionId.toString()
        }
    }

    @Test
    fun adminAppendsARejectDisposition() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(aRejectDispositionRequest().withEntryId(rejectEntryId))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To rejectEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "parentEntryId" To decisionEntryId.toString()
                "seq" To 2
                "disposition" To "REJECT"
                noExtraProps()
            }
        }

        and(theMdrEntryInTheDatabaseIs(rejectEntryId)) {
            disposition shouldBe "REJECT"
            editedOutput shouldBe null
            parentEntryId shouldBe decisionEntryId
            humanId shouldBe someAdminUserId
        }
    }

    @Test
    fun adminAppendsAnEditDispositionCarryingTheFullEditedOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anEditDispositionRequest().withEntryId(editEntryId))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To editEntryId.toString()
                "decisionId" To someDecisionId.toString()
                "parentEntryId" To decisionEntryId.toString()
                "seq" To 2
                "disposition" To "EDIT"
                noExtraProps()
            }
        }

        and(theMdrEntryInTheDatabaseIs(editEntryId)) {
            disposition shouldBe "EDIT"
            editedOutput shouldBe "APPROVE with monitoring: raise the review threshold."
        }

        and(theMdrEntriesInTheDatabase()) {
            toHex(this[1]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[1])
        }
    }

    @Test
    fun editRedactsTheEditedOutputBeforeHashingAndPersisting() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(
                        anEditDispositionRequest()
                            .withEntryId(editEntryId)
                            .withEditedOutput("Reissue to card 9123456789012345 instead"),
                    )
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        and(theMdrEntryInTheDatabaseIs(editEntryId)) {
            editedOutput shouldBe "Reissue to card [REDACTED] instead"
        }

        and(theMdrEntriesInTheDatabase()) {
            toHex(this[1]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[1])
        }
    }

    @Test
    fun multipleDispositionsOnOneDecisionAppendCleanlyEachLinkingTheSameDecision() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anAcceptDispositionRequest().withEntryId(acceptEntryId))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(aRejectDispositionRequest().withEntryId(rejectEntryId))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 3
            (this[0]["entry_kind"] as String) shouldBe "MODEL_DECISION"
            (this[1]["entry_kind"] as String) shouldBe "HUMAN_DISPOSITION"
            (this[2]["entry_kind"] as String) shouldBe "HUMAN_DISPOSITION"

            (this[1]["disposition"] as String) shouldBe "ACCEPT"
            (this[2]["disposition"] as String) shouldBe "REJECT"

            (this[1]["parent_entry_id"] as UUID) shouldBe decisionEntryId
            (this[2]["parent_entry_id"] as UUID) shouldBe decisionEntryId

            (this[1]["prev_hash"] as ByteArray) shouldBe (this[0]["current_hash"] as ByteArray)
            (this[2]["prev_hash"] as ByteArray) shouldBe (this[1]["current_hash"] as ByteArray)
            toHex(this[1]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[1])
            toHex(this[2]["current_hash"] as ByteArray) shouldBe recomputedCurrentHashHex(this[2])
        }

        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 3L
        }

        and(theNumberOfAuditEventsWithAction("MDR_DISPOSITION_RECORDED")) {
            this shouldBe 2L
        }
    }
}
