package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.Client
import com.malai.test.acceptance.kensa.MalaiParty.Companion.MdrService
import com.malai.test.e2e.MalaiE2ETest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class MdrTest : MalaiE2ETest() {

    private val adminUserId = UUID.fromString("ad000000-0000-0000-0000-0000000000e2")
    private val decisionEntryId = UUID.fromString("e2e00000-0000-0000-0000-000000000001")
    private val decisionId = UUID.fromString("e2edec00-0000-0000-0000-000000000001")
    private val dispositionEntryId = UUID.fromString("e2e00000-0000-0000-0000-000000000002")

    @Test
    fun aModelDecisionIsRecordedRuledOnAndTheChainVerifiesIntact() = malaiTest {
        given(theMdrChainIsAtGenesis())

        whenever(mdrServiceRecordsCompletedModelDecision(decisionEntryId, decisionId))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To decisionEntryId.toString()
                "decisionId" To decisionId.toString()
                "seq" To 1
                "outcome" To "COMPLETED"
                noExtraProps()
            }
        }

        whenever(mdrServiceRecordsAcceptDisposition(dispositionEntryId, decisionId, adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To dispositionEntryId.toString()
                "decisionId" To decisionId.toString()
                "parentEntryId" To decisionEntryId.toString()
                "seq" To 2
                "disposition" To "ACCEPT"
                noExtraProps()
            }
        }

        whenever(mdrServiceListsDecisions(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    json {
                        "decisionId" To decisionId.toString()
                        "entryId" To decisionEntryId.toString()
                        "seq" To 1
                        "ts" To something
                        "outcome" To "COMPLETED"
                        "modelId" To "claude-opus-4-8"
                        "modelVersion" To "2026-01"
                        "correlationType" To "PAYMENT"
                        "correlationId" To "pmt-e2e-1"
                        "advisory" To false
                        "dispositionCount" To 1
                        "latencyMs" To 842
                        "costMicros" To 1500
                        "costCurrency" To "GBP"
                        "rulingInForce" To "ACCEPT"
                        "verdict" To json {
                            "decision" To "APPROVE"
                            "rationale" To "no anomalies"
                            noExtraProps()
                        }
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        whenever(mdrServiceVerifiesChain(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To 2
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 2

            this[0].seq shouldBe 1L
            this[0].entryKind shouldBe "MODEL_DECISION"
            this[0].outcome shouldBe "COMPLETED"
            this[0].entryId shouldBe decisionEntryId

            this[1].seq shouldBe 2L
            this[1].entryKind shouldBe "HUMAN_DISPOSITION"
            this[1].disposition shouldBe "ACCEPT"
            this[1].entryId shouldBe dispositionEntryId
            this[1].parentEntryId shouldBe decisionEntryId
            this[1].prevHash shouldBe this[0].currentHash
        }

        and(theFollowingHttpRequestWasMade(Client, MdrService) {
            method == POST && url == "/api/v1/internal/mdr/decisions"
        }) {
            method shouldBe POST
        }

        and(theFollowingHttpRequestWasMade(Client, MdrService) {
            method == POST && url == "/api/v1/admin/mdr/decisions/$decisionId/dispositions"
        }) {
            method shouldBe POST
        }
    }
}
