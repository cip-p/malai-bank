package com.malai.test.e2e.tests

import tools.jackson.module.kotlin.readValue
import com.malai.test.acceptance.kensa.MalaiParty.Companion.MdrService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.PaymentsService
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.fixtures.readAccountSortCodeAndNumber
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import dev.kensa.StateCollector
import io.kotest.matchers.collections.shouldHaveSize
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
private const val WATCHLIST_VERSION = "2026-07-01"

private const val ANALYST_APPROVAL =
    """{"decision":"APPROVE","riskScore":25,"reasons":["WATCHLIST_FALSE_POSITIVE"]}"""

class PaymentScreeningTest : MalaiE2ETest() {

    private val adminUserId = UUID.fromString("ad000000-0000-0000-0000-0000000000e3")
    private val acceptEntryId = UUID.fromString("e2e00000-0000-0000-0000-0000000000a1")
    private val editEntryId = UUID.fromString("e2e00000-0000-0000-0000-0000000000a2")

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun aFlaggedCustomerPaymentParksInReferredAndItsDecisionWaitsForARulingInTheAdminWorklist() = malaiTest {
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
        val (sendersSortCode, sendersAccountNumber) = readAccountSortCodeAndNumber(
            accountId = sendersAccountId,
            userId = someUserId(),
            accountHolderId = sendersAccountHolderId,
        )

        and(theFollowingBalanceIsSeededOnAccount(
            accountId = sendersAccountId,
            amountMinorUnits = 20_000L,
        ))

        whenever(paymentsServiceReceivesInitiatePayment(
            userId = someUserId(),
            accountHolderId = sendersAccountHolderId,
            idempotencyKey = "e2e-screening-referred-1",
            fromAccountId = sendersAccountId,
            toSortCode = WATCHLIST_SORT_CODE,
            toAccountNumber = WATCHLIST_ACCOUNT_NUMBER,
            toName = WATCHLIST_COUNTERPARTY,
            amountMinorUnits = 2_500L,
            description = "to a watchlisted counterparty",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To something
                "state" To "REFERRED"
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

        val paymentId = uuidFromLastResponse("paymentId")

        and(theHoldsForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 2_500L
            this[0].status shouldBe "PLACED"
        }

        and(theLedgerEntriesForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 20_000L
        }

        whenever(mdrServiceListsDecisions(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    json {
                        "decisionId" To something
                        "entryId" To something
                        "seq" To 1
                        "ts" To something
                        "outcome" To "COMPLETED"
                        "modelId" To "malai-screening-rules"
                        "modelVersion" To "1"
                        "correlationType" To "PAYMENT"
                        "correlationId" To paymentId.toString()
                        "advisory" To false
                        "dispositionCount" To 0
                        "latencyMs" To something
                        "costMicros" To 0
                        "costCurrency" To "GBP"
                        "verdict" To json {
                            "decision" To "DECLINE"
                            "riskScore" To 25
                            "reasons" To listOf("WATCHLIST_COUNTERPARTY")
                            noExtraProps()
                        }
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        val decisionId = uuidFromLastResponseList("decisions", 0, "decisionId")

        whenever(mdrServiceReplaysDecision(adminUserId, decisionId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To decisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To paymentId.toString()
                "modelDecision" To json {
                    "entryId" To something
                    "seq" To 1
                    "ts" To something
                    "outcome" To "COMPLETED"
                    "modelId" To "malai-screening-rules"
                    "modelVersion" To "1"
                    "advisory" To false
                    "promptTemplateId" To "payment-screening"
                    "promptTemplateVersion" To "v1"
                    "promptTemplateHash" To something
                    "renderedPrompt" To something
                    "retrievedContext" To listOf(
                        json {
                            "type" To "ACCOUNT"
                            "id" To sendersAccountId.toString()
                            "contentHash" To something
                            noExtraProps()
                        },
                        json {
                            "type" To "WATCHLIST"
                            "id" To WATCHLIST_VERSION
                            "contentHash" To something
                            noExtraProps()
                        },
                    )
                    "rawOutput" To """{"decision":"DECLINE","riskScore":25,"reasons":["WATCHLIST_COUNTERPARTY"]}"""
                    "verdict" To json {
                        "decision" To "DECLINE"
                        "riskScore" To 25
                        "reasons" To listOf("WATCHLIST_COUNTERPARTY")
                        noExtraProps()
                    }
                    "latencyMs" To something
                    "costMicros" To 0
                    "costCurrency" To "GBP"
                    noExtraProps()
                }
                "dispositions" To emptyList<Any>()
                noExtraProps()
            }
        }

        and(theRenderedPromptOfTheReplayedDecision()) {
            this shouldContain "  id: $paymentId"
            this shouldContain "  rail: FPS"
            this shouldContain "  amount: 2500 minor units of GBP"
            this shouldContain "  beneficiary name: $WATCHLIST_COUNTERPARTY"
            this shouldContain "  beneficiary identifier: $WATCHLIST_SORT_CODE ****${WATCHLIST_ACCOUNT_NUMBER.takeLast(4)}"
            this shouldContain "  identifier: $sendersSortCode ****${sendersAccountNumber.takeLast(4)}"
            this shouldContain "  version: $WATCHLIST_VERSION"
            this shouldNotContain sendersAccountNumber
            this shouldNotContain WATCHLIST_ACCOUNT_NUMBER
            this shouldNotContain "{{"
        }

        whenever(mdrServiceVerifiesChain(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To 1
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1

            this[0].seq shouldBe 1L
            this[0].entryKind shouldBe "MODEL_DECISION"
            this[0].outcome shouldBe "COMPLETED"
            this[0].decisionId shouldBe decisionId
            this[0].parentEntryId shouldBe null
        }

        and(thePaymentStaysInStateInTheDatabase(paymentId, "REFERRED"))

        and(theFollowingHttpRequestWasMade(PaymentsService, MdrService) {
            method == POST && url.endsWith("/api/v1/internal/mdr/decisions")
        }) {
            method shouldBe POST
        }
    }

    @Test
    fun acceptingTheModelsDeclineOnAReferredPaymentDeclinesItAndGivesTheCustomerTheirMoneyBack() = malaiTest {
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
            idempotencyKey = "e2e-screening-declined-1",
            fromAccountId = sendersAccountId,
            toSortCode = WATCHLIST_SORT_CODE,
            toAccountNumber = WATCHLIST_ACCOUNT_NUMBER,
            toName = WATCHLIST_COUNTERPARTY,
            amountMinorUnits = 2_500L,
            description = "to a watchlisted counterparty",
        ))

        val paymentId = uuidFromLastResponse("paymentId")

        whenever(mdrServiceListsDecisions(adminUserId))

        val decisionId = uuidFromLastResponseList("decisions", 0, "decisionId")

        whenever(mdrServiceRecordsAcceptDisposition(acceptEntryId, decisionId, adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To acceptEntryId.toString()
                "decisionId" To decisionId.toString()
                "parentEntryId" To something
                "seq" To 2
                "disposition" To "ACCEPT"
                noExtraProps()
            }
        }

        and(thePaymentReachesStateInTheDatabase(paymentId, "DECLINED"))

        and(theHoldsForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 2_500L
            this[0].status shouldBe "RELEASED"
        }

        and(theLedgerEntriesForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 20_000L
        }

        whenever(paymentsServiceReceivesGetPayment(someUserId(), sendersAccountHolderId, paymentId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "paymentId" To paymentId.toString()
                "state" To "DECLINED"
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

        whenever(mdrServiceListsDecisions(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisions" To listOf(
                    json {
                        "decisionId" To decisionId.toString()
                        "entryId" To something
                        "seq" To 1
                        "ts" To something
                        "outcome" To "COMPLETED"
                        "modelId" To "malai-screening-rules"
                        "modelVersion" To "1"
                        "correlationType" To "PAYMENT"
                        "correlationId" To paymentId.toString()
                        "advisory" To false
                        "dispositionCount" To 1
                        "latencyMs" To something
                        "costMicros" To 0
                        "costCurrency" To "GBP"
                        "rulingInForce" To "ACCEPT"
                        "verdict" To json {
                            "decision" To "DECLINE"
                            "riskScore" To 25
                            "reasons" To listOf("WATCHLIST_COUNTERPARTY")
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

            this[1].seq shouldBe 2L
            this[1].entryKind shouldBe "HUMAN_DISPOSITION"
            this[1].disposition shouldBe "ACCEPT"
            this[1].entryId shouldBe acceptEntryId
            this[1].parentEntryId shouldBe this[0].entryId
            this[1].prevHash shouldBe this[0].currentHash
        }
    }

    @Test
    fun editingTheModelsDeclineToAnApprovalReleasesTheReferredPaymentToTheRailAndItSettles() = malaiTest {
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
            idempotencyKey = "e2e-screening-released-1",
            fromAccountId = sendersAccountId,
            toSortCode = WATCHLIST_SORT_CODE,
            toAccountNumber = WATCHLIST_ACCOUNT_NUMBER,
            toName = WATCHLIST_COUNTERPARTY,
            amountMinorUnits = 2_500L,
            description = "to a watchlisted counterparty",
        ))

        val paymentId = uuidFromLastResponse("paymentId")

        whenever(mdrServiceListsDecisions(adminUserId))

        val decisionId = uuidFromLastResponseList("decisions", 0, "decisionId")

        whenever(mdrServiceRecordsEditDisposition(editEntryId, decisionId, adminUserId, ANALYST_APPROVAL))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To editEntryId.toString()
                "decisionId" To decisionId.toString()
                "parentEntryId" To something
                "seq" To 2
                "disposition" To "EDIT"
                noExtraProps()
            }
        }

        and(thePaymentReachesStateInTheDatabase(paymentId, "SETTLED"))

        whenever(paymentsServiceReceivesGetPayment(someUserId(), sendersAccountHolderId, paymentId))

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

        and(theHoldsForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 2_500L
            this[0].status shouldBe "SETTLED"
        }

        and(theLedgerEntriesForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 2
            this[0].amountMinorUnits shouldBe 20_000L
            this[1].amountMinorUnits shouldBe -2_500L
        }

        whenever(mdrServiceReplaysDecision(adminUserId, decisionId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "decisionId" To decisionId.toString()
                "correlationType" To "PAYMENT"
                "correlationId" To paymentId.toString()
                "modelDecision" To something
                "dispositions" To listOf(
                    json {
                        "entryId" To editEntryId.toString()
                        "seq" To 2
                        "ts" To something
                        "disposition" To "EDIT"
                        "humanId" To adminUserId.toString()
                        "parentEntryId" To something
                        "editedOutput" To ANALYST_APPROVAL
                        "diff" To something
                        noExtraProps()
                    },
                )
                "rulingInForce" To "EDIT"
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
    }

    private fun theRenderedPromptOfTheReplayedDecision() = StateCollector { _ ->
        val body = lastResponse().body ?: throw AssertionError("Response body is null")
        val json = testingObjectMapper.readValue<Map<String, Any>>(body)

        @Suppress("UNCHECKED_CAST")
        val modelDecision = json["modelDecision"] as Map<String, Any>
        modelDecision["renderedPrompt"] as String
    }
}
