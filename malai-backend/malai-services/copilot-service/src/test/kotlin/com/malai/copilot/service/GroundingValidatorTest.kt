package com.malai.copilot.service

import com.malai.copilot.model.SourceRecord
import com.malai.copilot.model.SourceRecordType
import com.malai.copilot.model.SourceRef
import com.malai.copilot.model.Timeline
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val SOME_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-0000000000b1")
private const val SOME_LEDGER_ENTRY_ID = "00000000-0000-4000-8000-0000000000a1"
private const val A_RECORD_THE_PLATFORM_NEVER_RETRIEVED = "00000000-0000-4000-8000-00000000dead"
private val SOME_MOMENT: Instant = Instant.parse("2026-05-03T12:00:00Z")

class GroundingValidatorTest {

    private val validator = GroundingValidator()

    private val thePaymentRecord = SourceRef(SourceRecordType.PAYMENT.name, SOME_PAYMENT_ID.toString())
    private val theLedgerEntryRecord = SourceRef(SourceRecordType.LEDGER_ENTRY.name, SOME_LEDGER_ENTRY_ID)

    @Test
    fun anAnswerWhoseEveryClaimCitesATimelineRecordIsGrounded() {
        validator.isGrounded(
            listOf(aClaimCiting(thePaymentRecord), aClaimCiting(theLedgerEntryRecord)),
            aTimeline(),
        ) shouldBe true
    }

    @Test
    fun oneClaimCitingARecordOutsideTheTimelineFailsTheWholeAnswer() {
        validator.isGrounded(
            listOf(
                aClaimCiting(thePaymentRecord),
                aClaimCiting(SourceRef(SourceRecordType.LEDGER_ENTRY.name, A_RECORD_THE_PLATFORM_NEVER_RETRIEVED)),
            ),
            aTimeline(),
        ) shouldBe false
    }

    @Test
    fun aClaimCitingNothingAtAllFailsTheWholeAnswerJustAsAFabricatedCitationDoes() {
        validator.isGrounded(
            listOf(aClaimCiting(thePaymentRecord), aClaimCiting()),
            aTimeline(),
        ) shouldBe false
    }

    @Test
    fun anAnswerCarryingNoClaimsIsNotGrounded() {
        validator.isGrounded(emptyList(), aTimeline()) shouldBe false
    }

    @Test
    fun citingTheSameTimelineRecordMoreThanOnceIsGrounded() {
        validator.isGrounded(
            listOf(
                aClaimCiting(thePaymentRecord, thePaymentRecord),
                aClaimCiting(thePaymentRecord, theLedgerEntryRecord),
            ),
            aTimeline(),
        ) shouldBe true
    }

    @Test
    fun aCitationOfTheRightIdOnTheWrongSurfaceIsNotATimelineRecord() {
        validator.isGrounded(
            listOf(aClaimCiting(SourceRef(SourceRecordType.AUDIT_EVENT.name, SOME_LEDGER_ENTRY_ID))),
            aTimeline(),
        ) shouldBe false
    }

    private fun aClaimCiting(vararg sources: SourceRef) = Claim(text = "some-claim-text", sources = sources.toList())

    private fun aTimeline() = Timeline(
        subjectType = "PAYMENT",
        subjectId = SOME_PAYMENT_ID,
        records = listOf(
            SourceRecord(
                type = SourceRecordType.PAYMENT,
                id = SOME_PAYMENT_ID.toString(),
                moment = SOME_MOMENT,
                summary = "state=SETTLED",
            ),
            SourceRecord(
                type = SourceRecordType.LEDGER_ENTRY,
                id = SOME_LEDGER_ENTRY_ID,
                moment = SOME_MOMENT,
                summary = "amountMinorUnits=-2500",
            ),
        ),
    )
}
