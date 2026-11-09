package com.malai.aml.sar

import com.malai.aml.monitoring.sha256Hex
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val SOME_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c1")
private val SOME_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000ac1")
private val SOME_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000ade1")
private const val SOME_PAYMENT_ID = "00000000-0000-4000-8000-0000000000e1"

class SarNarrativePromptTest {

    private val prompt = SarNarrativePrompt()

    @Test
    fun pinsTheTemplateByIdVersionAndGenuineContentHash() {
        prompt.templateId shouldBe "sar-narrative"
        prompt.templateVersion shouldBe "v1"
        prompt.templateHash shouldBe sha256Hex(
            javaClass.getResourceAsStream("/aml/sar-narrative-v1.txt")!!
                .readBytes().toString(Charsets.UTF_8),
        )
    }

    @Test
    fun rendersTheCaseAndOneLinePerTimelineRecord() {
        val rendered = prompt.render(aTimeline())

        rendered shouldContain "  id: $SOME_CASE_ID"
        rendered shouldContain "  subject account: $SOME_ACCOUNT_ID"
        rendered shouldContain "  opened at: 2026-10-15T10:00:00Z"
        rendered shouldContain
            "  ALERT_DECISION $SOME_ALERT_ID at 2026-10-15T10:15:00Z :: typology=STRUCTURING riskScore=70"
        rendered shouldContain
            "  PAYMENT $SOME_PAYMENT_ID at 2026-10-15T09:55:00Z :: amountMinorUnits=199900 beneficiary=****5678"
    }

    @Test
    fun leavesNoUnresolvedPlaceholders() {
        prompt.render(aTimeline()) shouldNotContain "{{"
    }

    private fun aTimeline() = SarTimeline(
        caseId = SOME_CASE_ID,
        accountId = SOME_ACCOUNT_ID,
        openedAt = Instant.parse("2026-10-15T10:00:00Z"),
        records = listOf(
            SarRecord(
                type = SarRecordType.PAYMENT,
                id = SOME_PAYMENT_ID,
                moment = Instant.parse("2026-10-15T09:55:00Z"),
                summary = "amountMinorUnits=199900 beneficiary=****5678",
            ),
            SarRecord(
                type = SarRecordType.ALERT_DECISION,
                id = SOME_ALERT_ID.toString(),
                moment = Instant.parse("2026-10-15T10:15:00Z"),
                summary = "typology=STRUCTURING riskScore=70",
            ),
        ),
    )
}
