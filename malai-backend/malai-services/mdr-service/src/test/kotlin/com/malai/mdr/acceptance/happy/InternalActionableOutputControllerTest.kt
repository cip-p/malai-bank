package com.malai.mdr.acceptance.happy

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class InternalActionableOutputControllerTest : MdrAcceptanceTest() {

    private val decisionEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000010")
    private val someDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000010")
    private val acceptEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000001")
    private val rejectEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000002")
    private val editEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000003")

    @Test
    fun actionableOutputUnderAnAcceptRulingServesTheRawOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId/actionable-output")
                    .withServiceJwtToken(),
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
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId/actionable-output")
                    .withServiceJwtToken(),
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
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId/actionable-output")
                    .withServiceJwtToken(),
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
    fun theInternalGateReadsAppendNothingToTheChain() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anAcceptIsRecorded(acceptEntryId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId/actionable-output")
                    .withServiceJwtToken(),
            ),
        )

        then(theMdrChainHeadInTheDatabase()) {
            this["last_seq"] shouldBe 2L
        }
    }

    private fun anAcceptIsRecorded(entryId: UUID) =
        anMdrHumanDispositionIsRecorded(entryId, someDecisionId, decisionEntryId, "ACCEPT")

    private fun aRejectIsRecorded(entryId: UUID) =
        anMdrHumanDispositionIsRecorded(entryId, someDecisionId, decisionEntryId, "REJECT")

    private fun anEditIsRecorded(entryId: UUID, editedOutput: String) =
        anMdrHumanDispositionIsRecorded(entryId, someDecisionId, decisionEntryId, "EDIT", editedOutput)
}
