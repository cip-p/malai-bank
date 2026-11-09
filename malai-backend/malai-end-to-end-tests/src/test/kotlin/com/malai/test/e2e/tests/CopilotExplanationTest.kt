package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.Client
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CopilotService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.MdrService
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.CitedSource
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import dev.kensa.StateCollector
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.util.UUID

private const val WATCHLIST_COUNTERPARTY = "Carol Singh"
private const val WATCHLIST_SORT_CODE = "30-00-03"
private const val WATCHLIST_ACCOUNT_NUMBER = "11223344"

private const val ANALYST_APPROVAL =
    """{"decision":"APPROVE","riskScore":25,"reasons":["WATCHLIST_FALSE_POSITIVE"]}"""

class CopilotExplanationTest : MalaiE2ETest() {

    private val adminUserId = UUID.fromString("ad000000-0000-0000-0000-0000000000e4")
    private val rulingEntryId = UUID.fromString("e2e00000-0000-0000-0000-0000000000b1")

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun anExplanationOfARuledPaymentIsRecordedAdvisoryOnTheChainAndEveryClaimCitesARecordThatResolves() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theMdrChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())

        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val sendersAccountId = capturedUuid(ACCOUNT_ID)
        val sendersAccountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)

        and(theFollowingBalanceIsSeededOnAccount(
            accountId = sendersAccountId,
            amountMinorUnits = 20_000L,
        ))

        whenever(paymentsServiceReceivesInitiatePayment(
            userId = someUserId(),
            accountHolderId = sendersAccountHolderId,
            idempotencyKey = "e2e-copilot-explained-1",
            fromAccountId = sendersAccountId,
            toSortCode = WATCHLIST_SORT_CODE,
            toAccountNumber = WATCHLIST_ACCOUNT_NUMBER,
            toName = WATCHLIST_COUNTERPARTY,
            amountMinorUnits = 2_500L,
            description = "to a watchlisted counterparty",
        ))

        val paymentId = uuidFromLastResponse("paymentId")

        whenever(mdrServiceListsDecisions(adminUserId))

        val screeningDecisionId = uuidFromLastResponseList("decisions", 0, "decisionId")

        whenever(mdrServiceRecordsEditDisposition(rulingEntryId, screeningDecisionId, adminUserId, ANALYST_APPROVAL))

        and(thePaymentReachesStateInTheDatabase(paymentId, "SETTLED"))

        whenever(copilotServiceExplainsPayment(adminUserId, paymentId, "e2e-copilot-explanation-1"))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To something
                "outcome" To "COMPLETED"
                "claims" To something
                noExtraProps()
            }
        }

        val explanationDecisionId = uuidFromLastResponse("decisionId")
        val citedSources = mutableListOf<CitedSource>()

        and(theSourcesCitedByTheExplanation()) {
            this.shouldNotBeEmpty()
            citedSources += this
            map { it.type }.toSet() shouldContainAll
                setOf("PAYMENT", "HOLD", "SCREENING_DECISION", "AUDIT_EVENT", "LEDGER_ENTRY")
        }

        val citedPaymentId = UUID.fromString(citedSources.first { it.type == "PAYMENT" }.id)
        val citedScreeningDecisionId = UUID.fromString(citedSources.first { it.type == "SCREENING_DECISION" }.id)

        whenever(paymentsServiceReceivesGetPayment(someUserId(), sendersAccountHolderId, citedPaymentId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "paymentId" To paymentId.toString()
                "state" To "SETTLED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To something
                "endToEndId" To something
                "fromAccountId" To sendersAccountId.toString()
                "toSortCode" To WATCHLIST_SORT_CODE
                "toAccountNumber" To WATCHLIST_ACCOUNT_NUMBER
                "toName" To WATCHLIST_COUNTERPARTY
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "to a watchlisted counterparty"
                noExtraProps()
            }
        }

        whenever(mdrServiceReplaysDecision(adminUserId, citedScreeningDecisionId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To screeningDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To paymentId.toString()
                "modelDecision" To something
                "dispositions" To something
                "rulingInForce" To "EDIT"
                noExtraProps()
            }
        }

        whenever(mdrServiceReplaysDecision(adminUserId, explanationDecisionId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To explanationDecisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To paymentId.toString()
                "modelDecision" To json {
                    "entryId" To something
                    "seq" To 3
                    "ts" To something
                    "outcome" To "COMPLETED"
                    "modelId" To "malai-copilot-template"
                    "modelVersion" To "v1"
                    "advisory" To true
                    "promptTemplateId" To "copilot-payment"
                    "promptTemplateVersion" To "v2"
                    "promptTemplateHash" To something
                    "renderedPrompt" To something
                    "retrievedContext" To something
                    "rawOutput" To something
                    "verdict" To something
                    "latencyMs" To something
                    "costMicros" To 0
                    "costCurrency" To "GBP"
                    noExtraProps()
                }
                "dispositions" To emptyList<Any>()
                noExtraProps()
            }
        }

        and(theRetrievedContextRefsOfTheReplayedDecision()) {
            this shouldContainAll citedSources.map { "${it.type}:${it.id}" }.toSet()
            this.distinct() shouldHaveSize this.size
        }

        and(theRenderedPromptOfTheReplayedDecision()) {
            this shouldContain paymentId.toString()
            this shouldContain screeningDecisionId.toString()
            this shouldNotContain WATCHLIST_ACCOUNT_NUMBER
            this shouldNotContain "{{"
        }

        whenever(mdrServiceVerifiesChain(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To 3
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 3

            this[0].seq shouldBe 1L
            this[0].entryKind shouldBe "MODEL_DECISION"
            this[0].decisionId shouldBe screeningDecisionId

            this[1].seq shouldBe 2L
            this[1].entryKind shouldBe "HUMAN_DISPOSITION"
            this[1].disposition shouldBe "EDIT"
            this[1].prevHash shouldBe this[0].currentHash

            this[2].seq shouldBe 3L
            this[2].entryKind shouldBe "MODEL_DECISION"
            this[2].decisionId shouldBe explanationDecisionId
            this[2].parentEntryId shouldBe null
            this[2].prevHash shouldBe this[1].currentHash
        }

        and(theFollowingHttpRequestWasMade(Client, CopilotService) {
            method == POST && url == "/api/v1/admin/copilot/explanations"
        }) {
            method shouldBe POST
        }

        and(theFollowingHttpRequestWasMade(CopilotService, MdrService) {
            method == POST && url.endsWith("/api/v1/internal/mdr/decisions")
        }) {
            method shouldBe POST
        }
    }

    private fun theRetrievedContextRefsOfTheReplayedDecision() = StateCollector { _ ->
        replayedModelDecision()["retrievedContext"]
            .values().map { "${it["type"].asString()}:${it["id"].asString()}" }
    }

    private fun theRenderedPromptOfTheReplayedDecision() = StateCollector { _ ->
        replayedModelDecision()["renderedPrompt"].asString()
    }

    private fun replayedModelDecision() =
        testingObjectMapper.readTree(lastResponse().body ?: throw AssertionError("Response body is null"))
            .at("/modelDecision")
}
