package com.malai.mdr.acceptance.happy

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class InternalDecisionRecordControllerTest : MdrAcceptanceTest() {

    private val decisionEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000110")
    private val someDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000110")
    private val acceptEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000101")
    private val rejectEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000102")
    private val somePaymentId = UUID.fromString("00000000-0000-4000-8000-000000000001")

    @Test
    fun theDecisionRecordServesTheRecordedFactsAndTheRulingInForce() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAlertDecisionIsRecorded())
        and(anMdrHumanDispositionIsRecorded(rejectEntryId, someDecisionId, decisionEntryId, "REJECT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId")
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "entryId" To decisionEntryId.toString()
                "seq" To 1
                "ts" To "2026-10-15T10:30:00Z"
                "outcome" To "COMPLETED"
                "advisory" To false
                "correlationType" To "ACCOUNT"
                "correlationId" To "acc-under-monitoring"
                "modelId" To "claude-opus-4-8"
                "modelVersion" To "2026-01"
                "verdict" To json {
                    "typology" To "STRUCTURING"
                    "riskScore" To 70
                    noExtraProps()
                }
                "retrievedContext" To listOf(
                    json {
                        "type" To "PAYMENT"
                        "id" To somePaymentId.toString()
                        "contentHash" To "ab".repeat(32)
                        noExtraProps()
                    },
                )
                "rulingInForce" To "REJECT"
                noExtraProps()
            }
        }
    }

    @Test
    fun theDecisionRecordOmitsTheRulingWhileTheDecisionAwaitsOne() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAlertDecisionIsRecorded())

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId")
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To someDecisionId.toString()
                "entryId" To decisionEntryId.toString()
                "seq" To 1
                "ts" To "2026-10-15T10:30:00Z"
                "outcome" To "COMPLETED"
                "advisory" To false
                "correlationType" To "ACCOUNT"
                "correlationId" To "acc-under-monitoring"
                "modelId" To "claude-opus-4-8"
                "modelVersion" To "2026-01"
                "verdict" To json {
                    "typology" To "STRUCTURING"
                    "riskScore" To 70
                    noExtraProps()
                }
                "retrievedContext" To listOf(
                    json {
                        "type" To "PAYMENT"
                        "id" To somePaymentId.toString()
                        "contentHash" To "ab".repeat(32)
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun theLatestRulingWinsOnTheDecisionRecord() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAlertDecisionIsRecorded())
        and(anMdrHumanDispositionIsRecorded(rejectEntryId, someDecisionId, decisionEntryId, "REJECT"))
        and(anMdrHumanDispositionIsRecorded(acceptEntryId, someDecisionId, decisionEntryId, "ACCEPT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId")
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "rulingInForce" To "ACCEPT"
            }
        }
    }

    @Test
    fun theDecisionRecordReadAppendsNothingToTheChain() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAlertDecisionIsRecorded())
        and(anMdrHumanDispositionIsRecorded(acceptEntryId, someDecisionId, decisionEntryId, "ACCEPT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId")
                    .withServiceJwtToken(),
            ),
        )

        then(theMdrChainHeadInTheDatabase()) {
            this["last_seq"] shouldBe 2L
        }
    }

    private fun anAlertDecisionIsRecorded() = anMdrModelDecisionIsRecorded(
        entryId = decisionEntryId,
        decisionId = someDecisionId,
        correlationType = "ACCOUNT",
        correlationId = "acc-under-monitoring",
        retrievedContext = listOf(
            mapOf("type" to "PAYMENT", "id" to somePaymentId.toString(), "contentHash" to "ab".repeat(32)),
        ),
        verdict = mapOf("typology" to "STRUCTURING", "riskScore" to 70),
    )
}
