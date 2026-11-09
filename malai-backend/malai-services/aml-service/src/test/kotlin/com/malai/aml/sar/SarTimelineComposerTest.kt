package com.malai.aml.sar

import com.malai.aml.model.AmlAlertView
import com.malai.aml.model.AmlEvidencePayment
import com.malai.aml.model.AmlRuling
import com.malai.aml.monitoring.AmlTypology
import com.malai.aml.monitoring.sha256Hex
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEventRecord
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c1")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ac1")
private val FIRST_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade1")
private val SECOND_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade2")
private val FIRST_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000e1")
private val SECOND_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000e2")
private val SOME_EVENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa1")

private val CASE_OPENED_AT: Instant = Instant.parse("2026-10-15T10:00:00Z")

class SarTimelineComposerTest {

    @Test
    fun theTimelineCarriesEveryAlertItsEvidenceAndTheCasesAuditEventsInChronologicalOrder() {
        val timeline = compose(
            alerts = listOf(aStructuringAlert(), aCorridorAlert()),
            auditEvents = listOf(theCaseOpenedEvent()),
        )

        timeline.records.map { it.type to it.id } shouldBe listOf(
            SarRecordType.PAYMENT to FIRST_PAYMENT_ID.toString(),
            SarRecordType.AUDIT_EVENT to SOME_EVENT_ID.toString(),
            SarRecordType.ALERT_DECISION to FIRST_ALERT_ID.toString(),
            SarRecordType.PAYMENT to SECOND_PAYMENT_ID.toString(),
            SarRecordType.ALERT_DECISION to SECOND_ALERT_ID.toString(),
        )
    }

    @Test
    fun anEvidencePaymentKeepsTheContentHashTheAlertPinnedRatherThanARecomputedOne() {
        val timeline = compose(alerts = listOf(aStructuringAlert()), auditEvents = emptyList())

        timeline.records
            .single { it.type == SarRecordType.PAYMENT }
            .contentHash shouldBe "a1".repeat(32)
    }

    @Test
    fun anAlertRecordIsHashedOverTheDecidedFactsTheOfficerSees() {
        val timeline = compose(alerts = listOf(aStructuringAlert()), auditEvents = emptyList())

        val alertRecord = timeline.records.single { it.type == SarRecordType.ALERT_DECISION }

        alertRecord.summary shouldBe "typology=STRUCTURING riskScore=70 rulingInForce=ACCEPT evidenceCount=1"
        alertRecord.contentHash shouldBe sha256Hex(
            "ALERT_DECISION|$FIRST_ALERT_ID|2026-10-15T10:15:00Z|${alertRecord.summary}",
        )
    }

    @Test
    fun anUnruledAlertSaysSoRatherThanSayingNothing() {
        val timeline = compose(
            alerts = listOf(aStructuringAlert(rulingInForce = null)),
            auditEvents = emptyList(),
        )

        timeline.records.single { it.type == SarRecordType.ALERT_DECISION }.summary shouldContain
            "rulingInForce=not ruled"
    }

    @Test
    fun aPaymentCitedByTwoAlertsAppearsInTheTimelineExactlyOnce() {
        val timeline = compose(
            alerts = listOf(aStructuringAlert(), aCorridorAlert(evidence = listOf(anEvidencePayment()))),
            auditEvents = emptyList(),
        )

        timeline.records.filter { it.type == SarRecordType.PAYMENT } shouldHaveSize 1
    }

    @Test
    fun theTimelineNeverCarriesAFullBeneficiaryAccountNumberOrIban() {
        val timeline = compose(alerts = listOf(aStructuringAlert(), aCorridorAlert()), auditEvents = emptyList())

        val summaries = timeline.records.joinToString(" ") { it.summary }

        summaries shouldContain "65-82-37 ****5678"
        summaries shouldNotContain "12345678"
        summaries shouldContain "******************0001"
        summaries shouldNotContain "KY12ABCD00000000000001"
    }

    @Test
    fun everyTimelineRecordIsAdmittedAsACitationAndNothingElseIs() {
        val timeline = compose(alerts = listOf(aStructuringAlert()), auditEvents = listOf(theCaseOpenedEvent()))

        timeline.records.all { timeline.admits(it.ref) } shouldBe true
        timeline.admits(SarRef(SarRecordType.PAYMENT.name, SECOND_PAYMENT_ID.toString())) shouldBe false
    }

    @Test
    fun theEvidenceAmountsAreCarriedForwardForTheDrafterToSee() {
        val timeline = compose(alerts = listOf(aStructuringAlert(), aCorridorAlert()), auditEvents = emptyList())

        timeline.evidenceAmountsMinorUnits shouldBe listOf(199_900L, 750_000L)
    }

    private fun compose(alerts: List<AmlAlertView>, auditEvents: List<AuditEventRecord>) =
        SarTimelineComposer.compose(
            caseId = SOME_CASE_ID,
            accountId = SOME_ACCOUNT_ID,
            openedAt = CASE_OPENED_AT,
            alerts = alerts,
            auditEvents = auditEvents,
        )

    private fun aStructuringAlert(rulingInForce: AmlRuling? = AmlRuling.ACCEPT) = AmlAlertView(
        decisionId = FIRST_ALERT_ID,
        typology = AmlTypology.STRUCTURING,
        riskScore = 70,
        raisedAt = Instant.parse("2026-10-15T10:15:00Z"),
        rulingInForce = rulingInForce,
        evidence = listOf(anEvidencePayment()),
    )

    private fun aCorridorAlert(evidence: List<AmlEvidencePayment> = listOf(aSwiftEvidencePayment())) = AmlAlertView(
        decisionId = SECOND_ALERT_ID,
        typology = AmlTypology.HIGH_RISK_CORRIDOR,
        riskScore = 80,
        raisedAt = Instant.parse("2026-10-15T10:30:00Z"),
        rulingInForce = null,
        evidence = evidence,
    )

    private fun anEvidencePayment() = AmlEvidencePayment(
        paymentId = FIRST_PAYMENT_ID,
        contentHash = "a1".repeat(32),
        rail = "FPS",
        direction = "OUTBOUND",
        amountMinorUnits = 199_900L,
        currency = "GBP",
        createdAt = Instant.parse("2026-10-15T09:55:00Z"),
        beneficiaryName = "Acme Holdings",
        beneficiarySortCode = "65-82-37",
        beneficiaryAccountNo = "12345678",
    )

    private fun aSwiftEvidencePayment() = AmlEvidencePayment(
        paymentId = SECOND_PAYMENT_ID,
        contentHash = "b2".repeat(32),
        rail = "SWIFT",
        direction = "OUTBOUND",
        amountMinorUnits = 750_000L,
        currency = "GBP",
        createdAt = Instant.parse("2026-10-15T10:20:00Z"),
        beneficiaryName = "Offshore Trading",
        beneficiaryIban = "KY12ABCD00000000000001",
        beneficiaryBic = "ABCDKYK1XXX",
    )

    private fun theCaseOpenedEvent() = AuditEventRecord(
        eventId = SOME_EVENT_ID,
        seq = 1L,
        ts = CASE_OPENED_AT,
        actorId = null,
        actorType = ActorType.SYSTEM,
        action = "AML_CASE_OPENED",
        resourceType = "AmlCase",
        resourceId = SOME_CASE_ID.toString(),
        beforeState = null,
        afterState = mapOf("status" to "OPEN", "accountId" to SOME_ACCOUNT_ID.toString()),
        traceId = null,
    )
}
