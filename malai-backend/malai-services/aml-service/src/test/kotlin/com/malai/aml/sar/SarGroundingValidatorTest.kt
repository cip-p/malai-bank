package com.malai.aml.sar

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c1")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ac1")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade1")
private const val SOME_PAYMENT_ID = "00000000-0000-4000-8000-0000000000e1"
private const val A_PAYMENT_THE_PLATFORM_NEVER_RETRIEVED = "00000000-0000-4000-8000-00000000dead"
private val SOME_MOMENT: Instant = Instant.parse("2026-10-15T10:00:00Z")

class SarGroundingValidatorTest {

    private val validator = SarGroundingValidator()

    private val theAlertRecord = SarRef(SarRecordType.ALERT_DECISION.name, SOME_ALERT_ID.toString())
    private val thePaymentRecord = SarRef(SarRecordType.PAYMENT.name, SOME_PAYMENT_ID)

    @Test
    fun aNarrativeWhoseEveryStatementCitesATimelineRecordIsGrounded() {
        validator.isGrounded(
            listOf(aStatementCiting(theAlertRecord), aStatementCiting(thePaymentRecord)),
            aTimeline(),
        ) shouldBe true
    }

    @Test
    fun oneStatementCitingARecordOutsideTheTimelineFailsTheWholeDraft() {
        validator.isGrounded(
            listOf(
                aStatementCiting(theAlertRecord),
                aStatementCiting(SarRef(SarRecordType.PAYMENT.name, A_PAYMENT_THE_PLATFORM_NEVER_RETRIEVED)),
            ),
            aTimeline(),
        ) shouldBe false
    }

    @Test
    fun aStatementCitingNothingAtAllFailsTheWholeDraftJustAsAFabricatedCitationDoes() {
        validator.isGrounded(
            listOf(aStatementCiting(theAlertRecord), aStatementCiting()),
            aTimeline(),
        ) shouldBe false
    }

    @Test
    fun aDraftCarryingNoStatementsIsNotGrounded() {
        validator.isGrounded(emptyList(), aTimeline()) shouldBe false
    }

    @Test
    fun aStatementRestingOnEveryRecordAtOnceIsGrounded() {
        validator.isGrounded(
            listOf(aStatementCiting(theAlertRecord, thePaymentRecord)),
            aTimeline(),
        ) shouldBe true
    }

    @Test
    fun aCitationOfTheRightIdOnTheWrongSurfaceIsNotATimelineRecord() {
        validator.isGrounded(
            listOf(aStatementCiting(SarRef(SarRecordType.AUDIT_EVENT.name, SOME_PAYMENT_ID))),
            aTimeline(),
        ) shouldBe false
    }

    private fun aStatementCiting(vararg sources: SarRef) =
        SarStatement(text = "some-statement-text", sources = sources.toList())

    private fun aTimeline() = SarTimeline(
        caseId = SOME_CASE_ID,
        accountId = SOME_ACCOUNT_ID,
        openedAt = SOME_MOMENT,
        records = listOf(
            SarRecord(
                type = SarRecordType.ALERT_DECISION,
                id = SOME_ALERT_ID.toString(),
                moment = SOME_MOMENT,
                summary = "typology=STRUCTURING",
            ),
            SarRecord(
                type = SarRecordType.PAYMENT,
                id = SOME_PAYMENT_ID,
                moment = SOME_MOMENT,
                summary = "amountMinorUnits=199900",
            ),
        ),
    )
}
