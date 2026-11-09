package com.malai.test.e2e.tests

import tools.jackson.module.kotlin.readValue
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AmlService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.Client
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
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import dev.kensa.StateCollector
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.util.UUID

private const val BENEFICIARY_NAME = "Dominic Fairweather"
private const val BENEFICIARY_SORT_CODE = "60-00-01"
private const val BENEFICIARY_ACCOUNT_NUMBER = "12345678"

private const val SUB_THRESHOLD_AMOUNT_MINOR_UNITS = 200_000

/**
 * The whole AML arc against the running stack.
 *
 * Screening deliberately cannot see this: each of the five payments is £2,000, well under the
 * £10,000 screening threshold, and the beneficiary is not on the watchlist. It is the post-event
 * monitoring sweep that joins them up — five sub-threshold payments to one beneficiary inside the
 * rolling window, jointly at the threshold — and raises the structuring alert that opens the case.
 *
 * From there the test drives the officer's path: rule the alert through the MDR, request the
 * grounded SAR draft, accept it, and close the case as reported through the Gate. Both hash chains
 * are then proved intact over everything that just happened — 'global' for the audit trail,
 * 'mdr' for the model-decision record.
 *
 * Threaded through the middle is the tipping-off invariant: with the case open on their account,
 * the customer reads their payment back and sees exactly what any other customer would.
 */
class AmlTest : MalaiE2ETest() {

    private val adminUserId = UUID.fromString("ad000000-0000-0000-0000-0000000000e4")
    private val alertRulingEntryId = UUID.fromString("e2e00000-0000-0000-0000-0000000000b1")
    private val sarRulingEntryId = UUID.fromString("e2e00000-0000-0000-0000-0000000000b2")

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun structuringSplitBelowTheScreeningThresholdIsCaughtRuledDraftedAndFiledWithBothChainsIntact() = malaiTest {
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
            amountMinorUnits = 1_500_000L,
        ))

        whenever(aSubThresholdPaymentTo(sendersAccountId, sendersAccountHolderId, "e2e-aml-structuring-1"))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To something
                "state" To "PENDING"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To something
                "endToEndId" To something
                "fromAccountId" To sendersAccountId.toString()
                "toSortCode" To BENEFICIARY_SORT_CODE
                "toAccountNumber" To BENEFICIARY_ACCOUNT_NUMBER
                "toName" To BENEFICIARY_NAME
                "amountMinorUnits" To SUB_THRESHOLD_AMOUNT_MINOR_UNITS
                "currency" To "GBP"
                "description" To "one of five"
                noExtraProps()
            }
        }

        val firstPaymentId = uuidFromLastResponse("paymentId")

        whenever(aSubThresholdPaymentTo(sendersAccountId, sendersAccountHolderId, "e2e-aml-structuring-2"))
        val secondPaymentId = uuidFromLastResponse("paymentId")

        whenever(aSubThresholdPaymentTo(sendersAccountId, sendersAccountHolderId, "e2e-aml-structuring-3"))
        val thirdPaymentId = uuidFromLastResponse("paymentId")

        whenever(aSubThresholdPaymentTo(sendersAccountId, sendersAccountHolderId, "e2e-aml-structuring-4"))
        val fourthPaymentId = uuidFromLastResponse("paymentId")

        whenever(aSubThresholdPaymentTo(sendersAccountId, sendersAccountHolderId, "e2e-aml-structuring-5"))
        val fifthPaymentId = uuidFromLastResponse("paymentId")

        and(thePaymentReachesStateInTheDatabase(firstPaymentId, "SETTLED"))
        and(thePaymentReachesStateInTheDatabase(secondPaymentId, "SETTLED"))
        and(thePaymentReachesStateInTheDatabase(thirdPaymentId, "SETTLED"))
        and(thePaymentReachesStateInTheDatabase(fourthPaymentId, "SETTLED"))
        and(thePaymentReachesStateInTheDatabase(fifthPaymentId, "SETTLED"))

        and(anAmlCaseForAccountAppears(sendersAccountId))

        whenever(amlServiceListsCases(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cases" To listOf(
                    json {
                        "caseId" To something
                        "accountId" To sendersAccountId.toString()
                        "status" To "OPEN"
                        "alertCount" To 1
                        "openedAt" To something
                        "updatedAt" To something
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        val caseId = uuidFromLastResponseList("cases", 0, "caseId")

        whenever(amlServiceReadsCase(adminUserId, caseId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "caseId" To caseId.toString()
                "accountId" To sendersAccountId.toString()
                "status" To "OPEN"
                "openedAt" To something
                "updatedAt" To something
                "alerts" To listOf(
                    json {
                        "decisionId" To something
                        "typology" To "STRUCTURING"
                        "riskScore" To 70
                        "raisedAt" To something
                        "evidence" To listOf(
                            anEvidencePaymentOf(firstPaymentId),
                            anEvidencePaymentOf(secondPaymentId),
                            anEvidencePaymentOf(thirdPaymentId),
                            anEvidencePaymentOf(fourthPaymentId),
                            anEvidencePaymentOf(fifthPaymentId),
                        )
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        val alertDecisionId = uuidFromLastResponseList("alerts", 0, "decisionId")

        whenever(paymentsServiceReceivesGetPayment(someUserId(), sendersAccountHolderId, firstPaymentId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "paymentId" To firstPaymentId.toString()
                "state" To "SETTLED"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To something
                "endToEndId" To something
                "fromAccountId" To sendersAccountId.toString()
                "toSortCode" To BENEFICIARY_SORT_CODE
                "toAccountNumber" To BENEFICIARY_ACCOUNT_NUMBER
                "toName" To BENEFICIARY_NAME
                "amountMinorUnits" To SUB_THRESHOLD_AMOUNT_MINOR_UNITS
                "currency" To "GBP"
                "description" To "one of five"
                noExtraProps()
            }
        }

        whenever(mdrServiceRecordsAcceptDisposition(alertRulingEntryId, alertDecisionId, adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To alertRulingEntryId.toString()
                "decisionId" To alertDecisionId.toString()
                "parentEntryId" To something
                "seq" To something
                "disposition" To "ACCEPT"
                noExtraProps()
            }
        }

        whenever(amlServiceDraftsTheSar(adminUserId, caseId, "e2e-aml-sar-draft-1"))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "decisionId" To something
                "outcome" To "COMPLETED"
                "narrative" To something
                noExtraProps()
            }
        }

        val sarDecisionId = uuidFromLastResponse("decisionId")

        and(theNarrativeOfTheLastResponse()) {
            this shouldContain "Malai Bank reports activity it considers suspicious on account $sendersAccountId"
            this shouldContain "investigated as case $caseId"
            this shouldContain "typology=STRUCTURING riskScore=70 rulingInForce=ACCEPT evidenceCount=5"
            this shouldContain "action=AML_CASE_OPENED"
            this.split("The account settled a payment at") shouldHaveSize 6
        }

        whenever(mdrServiceRecordsAcceptDisposition(sarRulingEntryId, sarDecisionId, adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "entryId" To sarRulingEntryId.toString()
                "decisionId" To sarDecisionId.toString()
                "parentEntryId" To something
                "seq" To something
                "disposition" To "ACCEPT"
                noExtraProps()
            }
        }

        whenever(amlServiceClosesCaseAsReported(adminUserId, caseId, "e2e-aml-close-reported-1"))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "caseId" To caseId.toString()
                "accountId" To sendersAccountId.toString()
                "status" To "CLOSED_REPORTED"
                "openedAt" To something
                "updatedAt" To something
                "alerts" To something
                "sar" To json {
                    "decisionId" To sarDecisionId.toString()
                    "rulingInForce" To "ACCEPT"
                    "narrative" To something
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        and(theAmlCasesForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 1
            this[0].caseId shouldBe caseId
            this[0].status shouldBe "CLOSED_REPORTED"
            this[0].alertDecisionIds shouldBe listOf(alertDecisionId)
            this[0].sarDecisionId shouldBe sarDecisionId
            this[0].sarNarrative!! shouldContain "case $caseId"
        }

        whenever(mdrServiceVerifiesChain(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To something
                noExtraProps()
            }
        }

        whenever(authServiceVerifiesAuditChain(adminUserId))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To something
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this.map { it["action"].toString() }.filter { it.startsWith("AML_") } shouldBe
                listOf("AML_CASE_OPENED", "AML_CASE_CLOSED_REPORTED")
        }

        and(theMdrEntriesInTheDatabase()) {
            val alertEntry = this.single { it.decisionId == alertDecisionId && it.entryKind == "MODEL_DECISION" }
            alertEntry.outcome shouldBe "COMPLETED"

            val alertRuling = this.single { it.entryId == alertRulingEntryId }
            alertRuling.entryKind shouldBe "HUMAN_DISPOSITION"
            alertRuling.parentEntryId shouldBe alertEntry.entryId

            val sarEntry = this.single { it.decisionId == sarDecisionId && it.entryKind == "MODEL_DECISION" }
            sarEntry.outcome shouldBe "COMPLETED"

            val sarRuling = this.single { it.entryId == sarRulingEntryId }
            sarRuling.entryKind shouldBe "HUMAN_DISPOSITION"
            sarRuling.parentEntryId shouldBe sarEntry.entryId
        }

        and(theFollowingHttpRequestWasMade(Client, AmlService) {
            method == GET && url == "/api/v1/admin/aml/cases/$caseId"
        }) {
            method shouldBe GET
        }

        and(theFollowingHttpRequestWasMade(AmlService, PaymentsService) {
            method == GET && url.contains("/api/v1/internal/payments/$firstPaymentId")
        }) {
            method shouldBe GET
        }

        and(theFollowingHttpRequestWasMade(AmlService, MdrService) {
            method == GET && url == "/api/v1/internal/mdr/decisions/$sarDecisionId/actionable-output"
        }) {
            method shouldBe GET
        }
    }

    private fun aSubThresholdPaymentTo(
        sendersAccountId: UUID,
        sendersAccountHolderId: UUID,
        idempotencyKey: String,
    ) = paymentsServiceReceivesInitiatePayment(
        userId = someUserId(),
        accountHolderId = sendersAccountHolderId,
        idempotencyKey = idempotencyKey,
        fromAccountId = sendersAccountId,
        toSortCode = BENEFICIARY_SORT_CODE,
        toAccountNumber = BENEFICIARY_ACCOUNT_NUMBER,
        toName = BENEFICIARY_NAME,
        amountMinorUnits = SUB_THRESHOLD_AMOUNT_MINOR_UNITS.toLong(),
        description = "one of five",
    )

    private fun anEvidencePaymentOf(paymentId: UUID) = json {
        "paymentId" To paymentId.toString()
        "contentHash" To something
        "rail" To "FPS"
        "direction" To "OUTBOUND"
        "amountMinorUnits" To SUB_THRESHOLD_AMOUNT_MINOR_UNITS
        "currency" To "GBP"
        "createdAt" To something
        "beneficiaryName" To BENEFICIARY_NAME
        "beneficiarySortCode" To BENEFICIARY_SORT_CODE
        "beneficiaryAccountNo" To BENEFICIARY_ACCOUNT_NUMBER
        noExtraProps()
    }

    private fun theNarrativeOfTheLastResponse() = StateCollector { _ ->
        val body = lastResponse().body ?: throw AssertionError("Response body is null")
        testingObjectMapper.readValue<Map<String, Any>>(body)["narrative"] as String
    }
}
