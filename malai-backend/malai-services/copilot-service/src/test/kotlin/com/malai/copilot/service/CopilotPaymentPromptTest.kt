package com.malai.copilot.service

import com.malai.copilot.model.SourceRecord
import com.malai.copilot.model.SourceRecordType
import com.malai.copilot.model.Timeline
import com.malai.copilot.model.TimelineBalances
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private const val THE_V1_TEMPLATE_HASH = "22001ccd0908a1780c7ea06c78ec962a96c80a5c0045382b0703ce0d78c31b15"

class CopilotPaymentPromptTest {

    private val prompt = CopilotPaymentPrompt()

    private val somePaymentId = UUID.fromString("00000000-0000-4000-8000-0000000000b1")
    private val someDecisionId = UUID.fromString("00000000-0000-4000-8000-0000000000f9")
    private val someAccountId = UUID.fromString("00000000-0000-4000-8000-0000000000a4")

    @Test
    fun theTemplateIsAtV2() {
        prompt.templateId shouldBe "copilot-payment"
        prompt.templateVersion shouldBe "v2"
    }

    @Test
    fun theHashFollowsTheBytesSoTheV2TemplateCannotCarryTheV1Hash() {
        prompt.templateHash shouldNotBe THE_V1_TEMPLATE_HASH
    }

    @Test
    fun theHashIsStableAcrossReadsOfTheSameBytes() {
        prompt.templateHash shouldBe CopilotPaymentPrompt().templateHash
    }

    @Test
    fun theTemplateNamesTheScreeningAndHoldRecordTypesTheTimelineNowCarries() {
        val rendered = prompt.render(aTimelineOf())

        rendered shouldContain "HOLD"
        rendered shouldContain "SCREENING_DECISION"
        rendered shouldContain "PAYMENT_REFERRED"
        rendered shouldContain "MDR_DISPOSITION_RECORDED"
    }

    @Test
    fun everyPlaceholderIsSubstitutedSoNoTemplateMarkerReachesTheDecider() {
        val rendered = prompt.render(aTimelineOf(
            SourceRecord(SourceRecordType.HOLD, somePaymentId.toString(), anInstant, "status=PLACED"),
            SourceRecord(SourceRecordType.SCREENING_DECISION, someDecisionId.toString(), anInstant, "disposition=APPROVED"),
        ))

        rendered shouldNotContain "{{"
        rendered shouldContain "PAYMENT $somePaymentId"
        rendered shouldContain "HOLD $somePaymentId at $anInstant :: status=PLACED"
        rendered shouldContain "SCREENING_DECISION $someDecisionId at $anInstant :: disposition=APPROVED"
        rendered shouldContain "heldAmountMinorUnits=15000"
    }

    private fun aTimelineOf(vararg records: SourceRecord) = Timeline(
        subjectType = "PAYMENT",
        subjectId = somePaymentId,
        records = records.toList(),
        balances = TimelineBalances(someAccountId, 250_000L, 15_000L, 235_000L),
    )

    private val anInstant: Instant = Instant.parse("2026-05-03T12:00:00Z")
}
