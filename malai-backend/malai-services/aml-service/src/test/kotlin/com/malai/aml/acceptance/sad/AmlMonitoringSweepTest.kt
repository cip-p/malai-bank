package com.malai.aml.acceptance.sad

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.monitoring.AlertIdentity
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.response.SettledPaymentsResponseBuilder.Companion.aSettledPaymentsResponse
import com.malai.client.malai.payments.builders.response.SettledPaymentsResponseBuilder.SettledPaymentBuilder
import com.malai.client.malai.payments.builders.response.SettledPaymentsResponseBuilder.SettledPaymentBuilder.Companion.aSettledPayment
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000acc")

private val WINDOW_PAYMENT_IDS: List<UUID> =
    (1..5).map { UUID.fromString("00000000-0000-4000-8000-0000000000f$it") }

private val FAILURE_HASH = AlertIdentity.evaluationFailureHash(SOME_ACCOUNT_ID, WINDOW_PAYMENT_IDS)
private val FAILURE_DECISION_ID = AlertIdentity.evaluationFailureDecisionId(FAILURE_HASH)
private val FAILURE_ENTRY_ID = AlertIdentity.evaluationFailureEntryId(FAILURE_HASH)

private const val ARMED_TIMEOUT_AMOUNT = 173_205L
private const val ARMED_ERROR_AMOUNT = 223_606L

private const val MDR_DECISIONS_URL = "/mdr-service/api/v1/internal/mdr/decisions"

class AmlMonitoringSweepTest : AmlAcceptanceTest() {

    @Test
    fun aDeciderTimeoutOverACandidateWindowRecordsATimedOutOutcomeAndOpensNoCase() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aWindowWithArmedAmount(ARMED_TIMEOUT_AMOUNT)))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(FAILURE_ENTRY_ID)
            .withDecisionId(FAILURE_DECISION_ID)
            .withOutcome("TIMED_OUT")))

        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            method shouldBe POST
            body containsProp json {
                "entryId" To FAILURE_ENTRY_ID.toString()
                "decisionId" To FAILURE_DECISION_ID.toString()
                "correlationType" To "ACCOUNT"
                "correlationId" To SOME_ACCOUNT_ID.toString()
                "advisory" To false
                "modelId" To "malai-aml-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "aml-monitoring"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To WINDOW_PAYMENT_IDS.map { paymentId ->
                    json {
                        "type" To "PAYMENT"
                        "id" To paymentId.toString()
                        "contentHash" To something
                        noExtraProps()
                    }
                }
                "outcome" To "TIMED_OUT"
                "latencyMs" To something
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }

        and(theAmlCasesInTheDatabase()) {
            this shouldHaveSize 0
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun aDeciderErrorOverACandidateWindowRecordsAnErroredOutcomeWithItsDetail() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aWindowWithArmedAmount(ARMED_ERROR_AMOUNT)))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(FAILURE_ENTRY_ID)
            .withDecisionId(FAILURE_DECISION_ID)
            .withOutcome("ERRORED")))

        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            method shouldBe POST
            body containsProp json {
                "entryId" To FAILURE_ENTRY_ID.toString()
                "decisionId" To FAILURE_DECISION_ID.toString()
                "correlationType" To "ACCOUNT"
                "correlationId" To SOME_ACCOUNT_ID.toString()
                "advisory" To false
                "modelId" To "malai-aml-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "aml-monitoring"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To WINDOW_PAYMENT_IDS.map { paymentId ->
                    json {
                        "type" To "PAYMENT"
                        "id" To paymentId.toString()
                        "contentHash" To something
                        noExtraProps()
                    }
                }
                "outcome" To "ERRORED"
                "errorDetail" To "aml decider errored over payment ${WINDOW_PAYMENT_IDS[2]}"
                "latencyMs" To something
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }

        and(theAmlCasesInTheDatabase()) {
            this shouldHaveSize 0
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    private fun aWindowWithArmedAmount(armedAmountMinorUnits: Long) =
        aSettledPaymentsResponse().withPayments(
            *WINDOW_PAYMENT_IDS.mapIndexed { index, paymentId ->
                aStructuringPayment(paymentId, minute = index + 1)
                    .withAmountMinorUnits(if (index == 2) armedAmountMinorUnits else 200_000L)
            }.toTypedArray(),
        )

    private fun aStructuringPayment(paymentId: UUID, minute: Int): SettledPaymentBuilder =
        aSettledPayment()
            .withPaymentId(paymentId)
            .withDebtorAccountId(SOME_ACCOUNT_ID)
            .withAmountMinorUnits(200_000L)
            .withCreatedAt("2026-10-15T10:0$minute:00Z")
            .withBeneficiaryName("Bob Harrison")
            .withBeneficiarySortCode("65-82-37")
            .withBeneficiaryAccountNo("87654331")
}
