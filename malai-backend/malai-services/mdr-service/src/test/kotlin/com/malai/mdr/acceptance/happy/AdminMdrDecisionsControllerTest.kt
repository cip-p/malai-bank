package com.malai.mdr.acceptance.happy

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class AdminMdrDecisionsControllerTest : MdrAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val decisionEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000010")
    private val someDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000010")
    private val acceptEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000001")
    private val rejectEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000002")
    private val editEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000003")
    private val dispositionHumanId = UUID.fromString("ad000000-0000-0000-0000-0000000000aa")
    private val timedOutEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000020")
    private val timedOutDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000020")
    private val erroredEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000030")
    private val erroredDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000030")
    private val advisoryEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000040")
    private val advisoryDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000040")

    @Test
    fun actionableOutputUnderAnAcceptRulingServesTheRawOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "ruling" To "ACCEPT"
                "rulingEntryId" To acceptEntryId.toString()
                "actionableOutput" To "APPROVE: low risk."
                noExtraProps()
            }
        }
    }

    @Test
    fun actionableOutputUnderAnEditRulingServesTheEditedOutputVerbatim() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anEditIsRecorded(editEntryId, "APPROVE with monitoring: raise the review threshold."))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "ruling" To "EDIT"
                "rulingEntryId" To editEntryId.toString()
                "actionableOutput" To "APPROVE with monitoring: raise the review threshold."
                noExtraProps()
            }
        }
    }

    @Test
    fun latestWinsARejectFollowedByAnAcceptServesTheOutputAgain() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(aRejectIsRecorded(rejectEntryId))
        and(anAcceptIsRecorded(acceptEntryId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "ruling" To "ACCEPT"
                "rulingEntryId" To acceptEntryId.toString()
                "actionableOutput" To "APPROVE: low risk."
                noExtraProps()
            }
        }
    }

    @Test
    fun replayReadReturnsTheFullEntryThreadInChainOrderWithTheRulingInForce() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))
        and(aRejectIsRecorded(rejectEntryId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To "pmt-verify"
                "modelDecision" To json {
                    "entryId" To decisionEntryId.toString()
                    "seq" To 1
                    "ts" To "2026-10-15T10:30:00Z"
                    "outcome" To "COMPLETED"
                    "modelId" To "claude-opus-4-8"
                    "modelVersion" To "2026-01"
                    "advisory" To false
                    "promptTemplateId" To "fraud-triage"
                    "promptTemplateVersion" To "v3"
                    "promptTemplateHash" To "01".repeat(32)
                    "renderedPrompt" To "Assess payment for fraud risk."
                    "rawOutput" To "APPROVE: low risk."
                    "latencyMs" To 10
                    "costMicros" To 5
                    "costCurrency" To "GBP"
                    noExtraProps()
                }
                "dispositions" To listOf(
                    json {
                        "entryId" To acceptEntryId.toString()
                        "seq" To 2
                        "ts" To "2026-10-15T10:30:00Z"
                        "disposition" To "ACCEPT"
                        "humanId" To dispositionHumanId.toString()
                        "parentEntryId" To decisionEntryId.toString()
                        noExtraProps()
                    },
                    json {
                        "entryId" To rejectEntryId.toString()
                        "seq" To 3
                        "ts" To "2026-10-15T10:30:00Z"
                        "disposition" To "REJECT"
                        "humanId" To dispositionHumanId.toString()
                        "parentEntryId" To decisionEntryId.toString()
                        noExtraProps()
                    },
                )
                "rulingInForce" To "REJECT"
                noExtraProps()
            }
        }
    }

    @Test
    fun replayReadDerivesTheLineDiffForAnEditDispositionFromTheImmutableOutputs() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId, "line one\nline two\nline three"))
        and(anEditIsRecorded(editEntryId, "line one\nline two changed\nline three"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To "pmt-verify"
                "modelDecision" To json {
                    "entryId" To decisionEntryId.toString()
                    "seq" To 1
                    "ts" To "2026-10-15T10:30:00Z"
                    "outcome" To "COMPLETED"
                    "modelId" To "claude-opus-4-8"
                    "modelVersion" To "2026-01"
                    "advisory" To false
                    "promptTemplateId" To "fraud-triage"
                    "promptTemplateVersion" To "v3"
                    "promptTemplateHash" To "01".repeat(32)
                    "renderedPrompt" To "Assess payment for fraud risk."
                    "rawOutput" To "line one\nline two\nline three"
                    "latencyMs" To 10
                    "costMicros" To 5
                    "costCurrency" To "GBP"
                    noExtraProps()
                }
                "dispositions" To listOf(
                    json {
                        "entryId" To editEntryId.toString()
                        "seq" To 2
                        "ts" To "2026-10-15T10:30:00Z"
                        "disposition" To "EDIT"
                        "humanId" To dispositionHumanId.toString()
                        "parentEntryId" To decisionEntryId.toString()
                        "editedOutput" To "line one\nline two changed\nline three"
                        "diff" To listOf(
                            json { "op" To "UNCHANGED"; "text" To "line one"; noExtraProps() },
                            json { "op" To "REMOVED"; "text" To "line two"; noExtraProps() },
                            json { "op" To "ADDED"; "text" To "line two changed"; noExtraProps() },
                            json { "op" To "UNCHANGED"; "text" To "line three"; noExtraProps() },
                        )
                        noExtraProps()
                    },
                )
                "rulingInForce" To "EDIT"
                noExtraProps()
            }
        }
    }

    @Test
    fun replayReadReimposesTheTypedRefShapeOnStoredRetrievedContext() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(
            decisionEntryId, someDecisionId,
            retrievedContext = listOf(mapOf(
                "type" to "AUTHORISATION_HISTORY",
                "id" to "acc-00000001-90d",
                "contentHash" to "1a2b".repeat(16),
                "retrievalNote" to "stored looseness stays out of the contract",
            )),
        ))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To "pmt-verify"
                "modelDecision" To json {
                    "entryId" To decisionEntryId.toString()
                    "seq" To 1
                    "ts" To "2026-10-15T10:30:00Z"
                    "outcome" To "COMPLETED"
                    "modelId" To "claude-opus-4-8"
                    "modelVersion" To "2026-01"
                    "advisory" To false
                    "promptTemplateId" To "fraud-triage"
                    "promptTemplateVersion" To "v3"
                    "promptTemplateHash" To "01".repeat(32)
                    "renderedPrompt" To "Assess payment for fraud risk."
                    "retrievedContext" To listOf(
                        json {
                            "type" To "AUTHORISATION_HISTORY"
                            "id" To "acc-00000001-90d"
                            "contentHash" To "1a2b".repeat(16)
                            noExtraProps()
                        },
                    )
                    "rawOutput" To "APPROVE: low risk."
                    "latencyMs" To 10
                    "costMicros" To 5
                    "costCurrency" To "GBP"
                    noExtraProps()
                }
                "dispositions" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun worklistListsEveryDecisionNewestFirstWithRulingInForceAndDispositionCount() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))
        and(aRejectIsRecorded(rejectEntryId))
        and(anMdrModelDecisionIsRecorded(timedOutEntryId, timedOutDecisionId, rawOutput = null, outcome = "TIMED_OUT"))
        and(anMdrModelDecisionIsRecorded(
            erroredEntryId, erroredDecisionId,
            rawOutput = null, outcome = "ERRORED", errorDetail = "upstream 503",
        ))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    json {
                        "decisionId" To erroredDecisionId.toString()
                        "entryId" To erroredEntryId.toString()
                        "seq" To 5
                        "ts" To "2026-10-15T10:30:00Z"
                        "outcome" To "ERRORED"
                        "modelId" To "claude-opus-4-8"
                        "modelVersion" To "2026-01"
                        "correlationType" To "PAYMENT"
                        "correlationId" To "pmt-verify"
                        "advisory" To false
                        "dispositionCount" To 0
                        "latencyMs" To 10
                        "costMicros" To 5
                        "costCurrency" To "GBP"
                        noExtraProps()
                    },
                    json {
                        "decisionId" To timedOutDecisionId.toString()
                        "entryId" To timedOutEntryId.toString()
                        "seq" To 4
                        "ts" To "2026-10-15T10:30:00Z"
                        "outcome" To "TIMED_OUT"
                        "modelId" To "claude-opus-4-8"
                        "modelVersion" To "2026-01"
                        "correlationType" To "PAYMENT"
                        "correlationId" To "pmt-verify"
                        "advisory" To false
                        "dispositionCount" To 0
                        "latencyMs" To 10
                        "costMicros" To 5
                        "costCurrency" To "GBP"
                        noExtraProps()
                    },
                    json {
                        "decisionId" To someDecisionId.toString()
                        "entryId" To decisionEntryId.toString()
                        "seq" To 1
                        "ts" To "2026-10-15T10:30:00Z"
                        "outcome" To "COMPLETED"
                        "modelId" To "claude-opus-4-8"
                        "modelVersion" To "2026-01"
                        "correlationType" To "PAYMENT"
                        "correlationId" To "pmt-verify"
                        "advisory" To false
                        "dispositionCount" To 2
                        "latencyMs" To 10
                        "costMicros" To 5
                        "costCurrency" To "GBP"
                        "rulingInForce" To "REJECT"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun worklistFilteredByAwaitingRulingReturnsOnlyUnruledDecisions() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))
        and(anMdrModelDecisionIsRecorded(timedOutEntryId, timedOutDecisionId, rawOutput = null, outcome = "TIMED_OUT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "AWAITING_RULING")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    json {
                        "decisionId" To timedOutDecisionId.toString()
                        "entryId" To timedOutEntryId.toString()
                        "seq" To 3
                        "ts" To "2026-10-15T10:30:00Z"
                        "outcome" To "TIMED_OUT"
                        "modelId" To "claude-opus-4-8"
                        "modelVersion" To "2026-01"
                        "correlationType" To "PAYMENT"
                        "correlationId" To "pmt-verify"
                        "advisory" To false
                        "dispositionCount" To 0
                        "latencyMs" To 10
                        "costMicros" To 5
                        "costCurrency" To "GBP"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun aRejectedDecisionIsClearedFromTheWorklistJustAsAnAcceptedOneIs() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(aRejectIsRecorded(rejectEntryId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "AWAITING_RULING")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To emptyList<Any>()
                noExtraProps()
            }
        }

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "REJECT")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    aDecisionSummary(
                        someDecisionId, decisionEntryId, seq = 1, advisory = false,
                        dispositionCount = 1, rulingInForce = "REJECT",
                    ),
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun worklistRulingFilterReflectsLatestWinsAfterACorrectedRuling() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))
        and(aRejectIsRecorded(rejectEntryId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "ACCEPT")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To emptyList<Any>()
                noExtraProps()
            }
        }

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "REJECT")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    json {
                        "decisionId" To someDecisionId.toString()
                        "entryId" To decisionEntryId.toString()
                        "seq" To 1
                        "ts" To "2026-10-15T10:30:00Z"
                        "outcome" To "COMPLETED"
                        "modelId" To "claude-opus-4-8"
                        "modelVersion" To "2026-01"
                        "correlationType" To "PAYMENT"
                        "correlationId" To "pmt-verify"
                        "advisory" To false
                        "dispositionCount" To 2
                        "latencyMs" To 10
                        "costMicros" To 5
                        "costCurrency" To "GBP"
                        "rulingInForce" To "REJECT"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun worklistCarriesAdvisoryAndNarrowsToEitherKind() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anMdrModelDecisionIsRecorded(advisoryEntryId, advisoryDecisionId, advisory = true))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    aDecisionSummary(advisoryDecisionId, advisoryEntryId, seq = 2, advisory = true),
                    aDecisionSummary(someDecisionId, decisionEntryId, seq = 1, advisory = false),
                )
                noExtraProps()
            }
        }

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("advisory", "true")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(aDecisionSummary(advisoryDecisionId, advisoryEntryId, seq = 2, advisory = true))
                noExtraProps()
            }
        }

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("advisory", "false")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(aDecisionSummary(someDecisionId, decisionEntryId, seq = 1, advisory = false))
                noExtraProps()
            }
        }
    }

    @Test
    fun aDecisionWrittenBeforeTheAdvisoryColumnExistedReadsBackAsNotAdvisory() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(theMdrEntryAtSeqPredatesTheAdvisoryColumn(seq = 1L))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("advisory", "false")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(aDecisionSummary(someDecisionId, decisionEntryId, seq = 1, advisory = false))
                noExtraProps()
            }
        }

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("advisory", "true")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun theReviewersDefaultViewIsNonAdvisoryDecisionsAwaitingARuling() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))
        and(anMdrModelDecisionIsRecorded(timedOutEntryId, timedOutDecisionId, rawOutput = null, outcome = "TIMED_OUT"))
        and(anMdrModelDecisionIsRecorded(advisoryEntryId, advisoryDecisionId, advisory = true))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "AWAITING_RULING")
                    .queryParam("advisory", "false")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    aDecisionSummary(
                        timedOutDecisionId, timedOutEntryId, seq = 3, advisory = false, outcome = "TIMED_OUT",
                    ),
                )
                noExtraProps()
            }
        }

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "AWAITING_RULING")
                    .queryParam("advisory", "true")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(aDecisionSummary(advisoryDecisionId, advisoryEntryId, seq = 4, advisory = true))
                noExtraProps()
            }
        }
    }

    @Test
    fun replayOfAnAdvisoryDecisionIsTheSameThreadAsAnyOther() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(advisoryEntryId, advisoryDecisionId, advisory = true))
        and(anMdrHumanDispositionIsRecorded(acceptEntryId, advisoryDecisionId, advisoryEntryId, "ACCEPT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$advisoryDecisionId")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To advisoryDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To "pmt-verify"
                "modelDecision" To json {
                    "entryId" To advisoryEntryId.toString()
                    "seq" To 1
                    "ts" To "2026-10-15T10:30:00Z"
                    "outcome" To "COMPLETED"
                    "modelId" To "claude-opus-4-8"
                    "modelVersion" To "2026-01"
                    "advisory" To true
                    "promptTemplateId" To "fraud-triage"
                    "promptTemplateVersion" To "v3"
                    "promptTemplateHash" To "01".repeat(32)
                    "renderedPrompt" To "Assess payment for fraud risk."
                    "rawOutput" To "APPROVE: low risk."
                    "latencyMs" To 10
                    "costMicros" To 5
                    "costCurrency" To "GBP"
                    noExtraProps()
                }
                "dispositions" To listOf(
                    json {
                        "entryId" To acceptEntryId.toString()
                        "seq" To 2
                        "ts" To "2026-10-15T10:30:00Z"
                        "disposition" To "ACCEPT"
                        "humanId" To dispositionHumanId.toString()
                        "parentEntryId" To advisoryEntryId.toString()
                        noExtraProps()
                    },
                )
                "rulingInForce" To "ACCEPT"
                noExtraProps()
            }
        }
    }

    @Test
    fun anAdvisoryDecisionStillAccruesRulingsAndTheGateAnswersUnchanged() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(advisoryEntryId, advisoryDecisionId, advisory = true))
        and(anMdrHumanDispositionIsRecorded(acceptEntryId, advisoryDecisionId, advisoryEntryId, "ACCEPT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$advisoryDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To advisoryDecisionId.toString()
                "ruling" To "ACCEPT"
                "rulingEntryId" To acceptEntryId.toString()
                "actionableOutput" To "APPROVE: low risk."
                noExtraProps()
            }
        }

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("advisory", "true")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    aDecisionSummary(
                        advisoryDecisionId, advisoryEntryId, seq = 1, advisory = true,
                        dispositionCount = 1, rulingInForce = "ACCEPT",
                    ),
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun worklistCarriesTheVerdictSoAReviewerSeesWhatEachDecisionConcluded() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(
            decisionEntryId, someDecisionId,
            verdict = mapOf("decision" to "REFER", "rule" to "WATCHLIST"),
        ))
        and(anMdrModelDecisionIsRecorded(timedOutEntryId, timedOutDecisionId, rawOutput = null, outcome = "TIMED_OUT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    aDecisionSummary(
                        timedOutDecisionId, timedOutEntryId, seq = 2, advisory = false, outcome = "TIMED_OUT",
                    ),
                    json {
                        "decisionId" To someDecisionId.toString()
                        "entryId" To decisionEntryId.toString()
                        "seq" To 1
                        "ts" To "2026-10-15T10:30:00Z"
                        "outcome" To "COMPLETED"
                        "modelId" To "claude-opus-4-8"
                        "modelVersion" To "2026-01"
                        "correlationType" To "PAYMENT"
                        "correlationId" To "pmt-verify"
                        "advisory" To false
                        "dispositionCount" To 0
                        "latencyMs" To 10
                        "costMicros" To 5
                        "costCurrency" To "GBP"
                        "verdict" To json {
                            "decision" To "REFER"
                            "rule" To "WATCHLIST"
                            noExtraProps()
                        }
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    private fun aDecisionSummary(
        decisionId: UUID,
        entryId: UUID,
        seq: Int,
        advisory: Boolean,
        outcome: String = "COMPLETED",
        dispositionCount: Int = 0,
        rulingInForce: String? = null,
    ) = json {
        "decisionId" To decisionId.toString()
        "entryId" To entryId.toString()
        "seq" To seq
        "ts" To "2026-10-15T10:30:00Z"
        "outcome" To outcome
        "modelId" To "claude-opus-4-8"
        "modelVersion" To "2026-01"
        "correlationType" To "PAYMENT"
        "correlationId" To "pmt-verify"
        "advisory" To advisory
        "dispositionCount" To dispositionCount
        "latencyMs" To 10
        "costMicros" To 5
        "costCurrency" To "GBP"
        rulingInForce?.let { "rulingInForce" To it }
        noExtraProps()
    }

    private fun anAcceptIsRecorded(entryId: UUID) =
        anMdrHumanDispositionIsRecorded(entryId, someDecisionId, decisionEntryId, "ACCEPT")

    private fun aRejectIsRecorded(entryId: UUID) =
        anMdrHumanDispositionIsRecorded(entryId, someDecisionId, decisionEntryId, "REJECT")

    private fun anEditIsRecorded(entryId: UUID, editedOutput: String) =
        anMdrHumanDispositionIsRecorded(entryId, someDecisionId, decisionEntryId, "EDIT", editedOutput)
}
