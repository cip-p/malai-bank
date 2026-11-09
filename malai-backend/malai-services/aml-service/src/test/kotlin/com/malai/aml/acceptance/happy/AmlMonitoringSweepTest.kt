package com.malai.aml.acceptance.happy

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.monitoring.AlertIdentity
import com.malai.aml.monitoring.AmlTypology
import com.malai.aml.monitoring.sha256Hex
import com.malai.aml.support.PersistedAmlCase
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
import java.time.Instant
import java.util.UUID

private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000acc")
private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ca5")
private val SOME_AUDIT_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aad")

private val EVIDENCE_PAYMENT_IDS: List<UUID> =
    (1..5).map { UUID.fromString("00000000-0000-4000-8000-00000000000$it") }

private val EVIDENCE_HASH =
    AlertIdentity.evidenceHash(SOME_ACCOUNT_ID, AmlTypology.STRUCTURING, EVIDENCE_PAYMENT_IDS)
private val ALERT_DECISION_ID = AlertIdentity.alertDecisionId(EVIDENCE_HASH)
private val ALERT_ENTRY_ID = AlertIdentity.alertEntryId(EVIDENCE_HASH)

private val SECOND_PATTERN_PAYMENT_IDS: List<UUID> =
    (6..8).map { UUID.fromString("00000000-0000-4000-8000-00000000000$it") }

private val SECOND_ALERT_DECISION_ID = AlertIdentity.alertDecisionId(
    AlertIdentity.evidenceHash(SOME_ACCOUNT_ID, AmlTypology.STRUCTURING, SECOND_PATTERN_PAYMENT_IDS),
)

private val CORRIDOR_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000c01")

private val CORRIDOR_EVIDENCE_HASH =
    AlertIdentity.evidenceHash(SOME_ACCOUNT_ID, AmlTypology.HIGH_RISK_CORRIDOR, listOf(CORRIDOR_PAYMENT_ID))
private val CORRIDOR_ALERT_DECISION_ID = AlertIdentity.alertDecisionId(CORRIDOR_EVIDENCE_HASH)
private val CORRIDOR_ALERT_ENTRY_ID = AlertIdentity.alertEntryId(CORRIDOR_EVIDENCE_HASH)

private val INBOUND_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000d01")
private val DRAIN_PAYMENT_IDS: List<UUID> =
    (2..3).map { UUID.fromString("00000000-0000-4000-8000-000000000d0$it") }

private val RAPID_IN_OUT_EVIDENCE_HASH = AlertIdentity.evidenceHash(
    SOME_ACCOUNT_ID, AmlTypology.RAPID_IN_OUT, listOf(INBOUND_PAYMENT_ID) + DRAIN_PAYMENT_IDS,
)
private val RAPID_IN_OUT_ALERT_DECISION_ID = AlertIdentity.alertDecisionId(RAPID_IN_OUT_EVIDENCE_HASH)
private val RAPID_IN_OUT_ALERT_ENTRY_ID = AlertIdentity.alertEntryId(RAPID_IN_OUT_EVIDENCE_HASH)

private const val MDR_DECISIONS_URL = "/mdr-service/api/v1/internal/mdr/decisions"
private const val SETTLED_PAYMENTS_URL = "/payments-service/api/v1/internal/payments/settled"

class AmlMonitoringSweepTest : AmlAcceptanceTest() {

    @Test
    fun fiveSubThresholdPaymentsToOneBeneficiaryRaiseOneAlertAndOpenTheAccountsCase() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_CASE_ID, SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            *EVIDENCE_PAYMENT_IDS.mapIndexed { index, paymentId ->
                aStructuringPayment(paymentId, minute = index + 1)
            }.toTypedArray(),
        )))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(ALERT_ENTRY_ID)
            .withDecisionId(ALERT_DECISION_ID)))

        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequest { url.startsWith(SETTLED_PAYMENTS_URL) }) {
            url shouldBe "$SETTLED_PAYMENTS_URL?from=2026-10-15T09%3A30%3A00Z&until=2026-10-15T10%3A30%3A00Z"
        }

        and(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            method shouldBe POST
            body containsProp json {
                "entryId" To ALERT_ENTRY_ID.toString()
                "decisionId" To ALERT_DECISION_ID.toString()
                "correlationType" To "ACCOUNT"
                "correlationId" To SOME_ACCOUNT_ID.toString()
                "advisory" To false
                "modelId" To "malai-aml-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "aml-monitoring"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To EVIDENCE_PAYMENT_IDS.map { paymentId ->
                    json {
                        "type" To "PAYMENT"
                        "id" To paymentId.toString()
                        "contentHash" To something
                        noExtraProps()
                    }
                }
                "outcome" To "COMPLETED"
                "rawOutput" To
                    """{"typology":"STRUCTURING","riskScore":70,"evidencePaymentIds":[""" +
                    EVIDENCE_PAYMENT_IDS.joinToString(",") { "\"$it\"" } + """]}"""
                "verdict" To json {
                    "typology" To "STRUCTURING"
                    "riskScore" To 70
                    "evidencePaymentIds" To EVIDENCE_PAYMENT_IDS.map { it.toString() }
                    noExtraProps()
                }
                "latencyMs" To something
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "OPEN",
                alertDecisionIds = listOf(ALERT_DECISION_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:30:00Z"),
                updatedAt = Instant.parse("2026-10-15T10:30:00Z"),
                closedAt = null,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "AML_CASE_OPENED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["resource_type"] shouldBe "AmlCase"
            this[0]["resource_id"] shouldBe SOME_CASE_ID.toString()
        }
    }

    @Test
    fun aPatternSeenByOverlappingSweepWindowsRaisesExactlyOneAlert() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_CASE_ID, SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            *EVIDENCE_PAYMENT_IDS.mapIndexed { index, paymentId ->
                aStructuringPayment(paymentId, minute = index + 1)
            }.toTypedArray(),
        )))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(ALERT_ENTRY_ID)
            .withDecisionId(ALERT_DECISION_ID)))

        whenever(theMonitoringSweepRuns())
        and(nowIs("2026-10-15T10:30:10Z"))
        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequests { url == MDR_DECISIONS_URL }) {
            this shouldHaveSize 1
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "OPEN",
                alertDecisionIds = listOf(ALERT_DECISION_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:30:00Z"),
                updatedAt = Instant.parse("2026-10-15T10:30:00Z"),
                closedAt = null,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun aSecondPatternForTheAccountJoinsTheExistingOpenCase() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_CASE_ID, SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            *EVIDENCE_PAYMENT_IDS.mapIndexed { index, paymentId ->
                aStructuringPayment(paymentId, minute = index + 1)
            }.toTypedArray(),
        )))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(ALERT_ENTRY_ID)
            .withDecisionId(ALERT_DECISION_ID)))

        whenever(theMonitoringSweepRuns())

        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            *(EVIDENCE_PAYMENT_IDS.mapIndexed { index, paymentId ->
                aStructuringPayment(paymentId, minute = index + 1)
            } + SECOND_PATTERN_PAYMENT_IDS.mapIndexed { index, paymentId ->
                aSecondBeneficiaryPayment(paymentId, minute = index + 10)
            }).toTypedArray(),
        )))
        and(nowIs("2026-10-15T10:31:00Z"))
        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequests { url == MDR_DECISIONS_URL }) {
            this shouldHaveSize 2
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "OPEN",
                alertDecisionIds = listOf(ALERT_DECISION_ID, SECOND_ALERT_DECISION_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:30:00Z"),
                updatedAt = Instant.parse("2026-10-15T10:31:00Z"),
                closedAt = null,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "AML_CASE_OPENED"
        }
    }

    @Test
    fun aSwiftPaymentToAHighRiskJurisdictionRaisesACorridorAlertPinningTheListVersion() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_CASE_ID, SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(aCorridorPayment())))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(CORRIDOR_ALERT_ENTRY_ID)
            .withDecisionId(CORRIDOR_ALERT_DECISION_ID)))

        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            method shouldBe POST
            body containsProp json {
                "entryId" To CORRIDOR_ALERT_ENTRY_ID.toString()
                "decisionId" To CORRIDOR_ALERT_DECISION_ID.toString()
                "correlationType" To "ACCOUNT"
                "correlationId" To SOME_ACCOUNT_ID.toString()
                "advisory" To false
                "modelId" To "malai-aml-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "aml-monitoring"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To listOf(
                    json {
                        "type" To "PAYMENT"
                        "id" To CORRIDOR_PAYMENT_ID.toString()
                        "contentHash" To something
                        noExtraProps()
                    },
                    json {
                        "type" To "JURISDICTION_LIST"
                        "id" To "2026-07-01"
                        "contentHash" To sha256Hex("2026-07-01\nQM\nZZ")
                        noExtraProps()
                    },
                )
                "outcome" To "COMPLETED"
                "rawOutput" To
                    """{"typology":"HIGH_RISK_CORRIDOR","riskScore":80,""" +
                    """"evidencePaymentIds":["$CORRIDOR_PAYMENT_ID"],"jurisdiction":"ZZ"}"""
                "verdict" To json {
                    "typology" To "HIGH_RISK_CORRIDOR"
                    "riskScore" To 80
                    "evidencePaymentIds" To listOf(CORRIDOR_PAYMENT_ID.toString())
                    "jurisdiction" To "ZZ"
                    noExtraProps()
                }
                "latencyMs" To something
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "OPEN",
                alertDecisionIds = listOf(CORRIDOR_ALERT_DECISION_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:30:00Z"),
                updatedAt = Instant.parse("2026-10-15T10:30:00Z"),
                closedAt = null,
            )
        }
    }

    @Test
    fun anInboundCreditRapidlyDrainedOutboundRaisesARapidInOutAlert() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_CASE_ID, SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            anInboundCredit(),
            aDrainPayment(DRAIN_PAYMENT_IDS[0], amountMinorUnits = 500_000L, minute = 15),
            aDrainPayment(DRAIN_PAYMENT_IDS[1], amountMinorUnits = 300_000L, minute = 20),
        )))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(RAPID_IN_OUT_ALERT_ENTRY_ID)
            .withDecisionId(RAPID_IN_OUT_ALERT_DECISION_ID)))

        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequest { url == MDR_DECISIONS_URL }) {
            method shouldBe POST
            body containsProp json {
                "entryId" To RAPID_IN_OUT_ALERT_ENTRY_ID.toString()
                "decisionId" To RAPID_IN_OUT_ALERT_DECISION_ID.toString()
                "correlationType" To "ACCOUNT"
                "correlationId" To SOME_ACCOUNT_ID.toString()
                "advisory" To false
                "modelId" To "malai-aml-rules"
                "modelVersion" To "1"
                "promptTemplateId" To "aml-monitoring"
                "promptTemplateVersion" To "v1"
                "promptTemplateHash" To something
                "renderedPrompt" To something
                "retrievedContext" To (listOf(INBOUND_PAYMENT_ID) + DRAIN_PAYMENT_IDS).map { paymentId ->
                    json {
                        "type" To "PAYMENT"
                        "id" To paymentId.toString()
                        "contentHash" To something
                        noExtraProps()
                    }
                }
                "outcome" To "COMPLETED"
                "rawOutput" To
                    """{"typology":"RAPID_IN_OUT","riskScore":75,"evidencePaymentIds":[""" +
                    (listOf(INBOUND_PAYMENT_ID) + DRAIN_PAYMENT_IDS).joinToString(",") { "\"$it\"" } +
                    """],"inboundPaymentId":"$INBOUND_PAYMENT_ID","drainedPercent":80}"""
                "verdict" To json {
                    "typology" To "RAPID_IN_OUT"
                    "riskScore" To 75
                    "evidencePaymentIds" To (listOf(INBOUND_PAYMENT_ID) + DRAIN_PAYMENT_IDS).map { it.toString() }
                    "inboundPaymentId" To INBOUND_PAYMENT_ID.toString()
                    "drainedPercent" To 80
                    noExtraProps()
                }
                "latencyMs" To something
                "costMicros" To 0
                "costCurrency" To "GBP"
                noExtraProps()
            }
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "OPEN",
                alertDecisionIds = listOf(RAPID_IN_OUT_ALERT_DECISION_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:30:00Z"),
                updatedAt = Instant.parse("2026-10-15T10:30:00Z"),
                closedAt = null,
            )
        }
    }

    @Test
    fun twoTypologiesOnOneAccountInOneSweepShareTheAccountsCase() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(SOME_CASE_ID, SOME_AUDIT_EVENT_ID))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            *(EVIDENCE_PAYMENT_IDS.mapIndexed { index, paymentId ->
                aStructuringPayment(paymentId, minute = index + 1)
            } + aCorridorPayment()).toTypedArray(),
        )))
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()
            .withEntryId(ALERT_ENTRY_ID)
            .withDecisionId(ALERT_DECISION_ID)))

        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequests { url == MDR_DECISIONS_URL }) {
            this shouldHaveSize 2
        }

        and(theAmlCaseForAccountInTheDatabase(SOME_ACCOUNT_ID)) {
            this shouldBe PersistedAmlCase(
                caseId = SOME_CASE_ID,
                accountId = SOME_ACCOUNT_ID,
                status = "OPEN",
                alertDecisionIds = listOf(ALERT_DECISION_ID, CORRIDOR_ALERT_DECISION_ID),
                sarDecisionId = null,
                sarNarrative = null,
                openedAt = Instant.parse("2026-10-15T10:30:00Z"),
                updatedAt = Instant.parse("2026-10-15T10:30:00Z"),
                closedAt = null,
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "AML_CASE_OPENED"
        }
    }

    @Test
    fun aQuietSweepRecordsNothing() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoAmlCasesInTheDatabase())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settledPaymentsReturns(aSettledPaymentsResponse().withPayments(
            aStructuringPayment(EVIDENCE_PAYMENT_IDS[0], minute = 1),
            aStructuringPayment(EVIDENCE_PAYMENT_IDS[1], minute = 2),
        )))

        whenever(theMonitoringSweepRuns())

        then(amlServiceMadeTheFollowingHttpRequests { url == MDR_DECISIONS_URL }) {
            this shouldHaveSize 0
        }

        and(theAmlCasesInTheDatabase()) {
            this shouldHaveSize 0
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    private fun aStructuringPayment(paymentId: UUID, minute: Int): SettledPaymentBuilder =
        aSettledPayment()
            .withPaymentId(paymentId)
            .withDebtorAccountId(SOME_ACCOUNT_ID)
            .withAmountMinorUnits(200_000L)
            .withCreatedAt("2026-10-15T10:0$minute:00Z")
            .withBeneficiaryName("Bob Harrison")
            .withBeneficiarySortCode("65-82-37")
            .withBeneficiaryAccountNo("87654331")

    private fun anInboundCredit(): SettledPaymentBuilder =
        aSettledPayment()
            .withPaymentId(INBOUND_PAYMENT_ID)
            .withDirection("INBOUND")
            .withBeneficiaryAccountId(SOME_ACCOUNT_ID)
            .withBeneficiarySortCode("65-82-71")
            .withBeneficiaryAccountNo("12345678")
            .withBeneficiaryName("Mule Account Holder")
            .withDebtorName("Ada Krantz")
            .withAmountMinorUnits(1_000_000L)
            .withCreatedAt("2026-10-15T10:10:00Z")

    private fun aDrainPayment(paymentId: UUID, amountMinorUnits: Long, minute: Int): SettledPaymentBuilder =
        aSettledPayment()
            .withPaymentId(paymentId)
            .withDebtorAccountId(SOME_ACCOUNT_ID)
            .withAmountMinorUnits(amountMinorUnits)
            .withCreatedAt("2026-10-15T10:$minute:00Z")
            .withBeneficiaryName("Dan Patel")
            .withBeneficiarySortCode("65-82-37")
            .withBeneficiaryAccountNo("11111111")

    private fun aCorridorPayment(): SettledPaymentBuilder =
        aSettledPayment()
            .withPaymentId(CORRIDOR_PAYMENT_ID)
            .withDebtorAccountId(SOME_ACCOUNT_ID)
            .withRail("SWIFT")
            .withAmountMinorUnits(2_500_000L)
            .withCreatedAt("2026-10-15T10:05:00Z")
            .withBeneficiaryName("Vex Marlowe")
            .withBeneficiaryIban("ZZ42MALA06580231234567")
            .withBeneficiaryBic("MALAZZQX")

    private fun aSecondBeneficiaryPayment(paymentId: UUID, minute: Int): SettledPaymentBuilder =
        aSettledPayment()
            .withPaymentId(paymentId)
            .withDebtorAccountId(SOME_ACCOUNT_ID)
            .withAmountMinorUnits(400_000L)
            .withCreatedAt("2026-10-15T10:$minute:00Z")
            .withBeneficiaryName("Dan Patel")
            .withBeneficiarySortCode("65-82-37")
            .withBeneficiaryAccountNo("11111111")
}
