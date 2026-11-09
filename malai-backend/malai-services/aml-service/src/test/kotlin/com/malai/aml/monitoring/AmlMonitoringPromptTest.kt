package com.malai.aml.monitoring

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AmlMonitoringPromptTest {

    private val properties = AmlProperties()
    private val prompt = AmlMonitoringPrompt(properties, HighRiskJurisdictionList(properties))

    private val accountId = UUID.fromString("00000000-0000-4000-9000-00000000acc1")
    private val paymentId = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
    private val until = Instant.parse("2026-10-15T10:30:00Z")
    private val from = until.minus(properties.structuring.window)

    @Test
    fun pinsTheTemplateByIdVersionAndGenuineContentHash() {
        prompt.templateId shouldBe "aml-monitoring"
        prompt.templateVersion shouldBe "v1"
        prompt.templateHash shouldBe sha256Hex(
            javaClass.getResourceAsStream("/aml/aml-monitoring-v1.txt")!!
                .readBytes().toString(Charsets.UTF_8),
        )
    }

    @Test
    fun rendersTheWindowFactsAndTheTypologyParameters() {
        val rendered = prompt.render(AmlWindow(accountId, from, until, listOf(aDomesticPayment())))

        rendered shouldBe """
            |You are the post-event transaction monitoring assistant for Malai Bank.
            |
            |Evaluate the account's settled payments in the rolling window below for
            |money-laundering typologies. You see only settled facts: no balances, no
            |customer profile, no history beyond this window.
            |
            |Detect STRUCTURING: at least 3 outbound payments to one beneficiary, each
            |under 1000000 minor units, jointly at or above it.
            |
            |Detect RAPID_IN_OUT: an inbound credit followed within PT15M by outbound
            |payments jointly moving at least 80% of it.
            |
            |Detect HIGH_RISK_CORRIDOR: an outbound SWIFT payment whose beneficiary
            |jurisdiction (IBAN characters 1-2, else BIC characters 5-6) is on the
            |high-risk jurisdiction list, version 2026-07-01: QM, ZZ.
            |
            |ACCOUNT
            |  id: $accountId
            |
            |WINDOW
            |  from: 2026-10-15T09:30:00Z (inclusive)
            |  until: 2026-10-15T10:30:00Z (exclusive)
            |
            |SETTLED PAYMENTS
            |  - $paymentId | OUTBOUND FPS | 200000 minor units of GBP | at 2026-10-15T10:25:00Z | to Bob Harrison 65-82-37 ****4331
            |
            |Respond with a single JSON object and nothing else:
            |{"hits": [{"typology": "STRUCTURING" | "RAPID_IN_OUT" | "HIGH_RISK_CORRIDOR", "riskScore": <integer 0-100>, "evidencePaymentIds": [<uuid>, ...]}, ...]}
            |
        """.trimMargin()
    }

    @Test
    fun rendersTheInternationalIdentifierFromBicAndMaskedIban() {
        val rendered = prompt.render(AmlWindow(accountId, from, until, listOf(aSwiftPayment())))

        rendered shouldContainLine
            "  - $paymentId | OUTBOUND SWIFT | 200000 minor units of GBP | at 2026-10-15T10:25:00Z | to Bob Harrison DEUTDEFFXXX ******************7890"
    }

    @Test
    fun neverExposesTheFullBeneficiaryAccountNumberOrIban() {
        val rendered = prompt.render(AmlWindow(accountId, from, until, listOf(aDomesticPayment(), aSwiftPayment())))

        rendered shouldNotContain "87654331"
        rendered shouldNotContain "DE89370400440532017890"
    }

    @Test
    fun leavesNoUnresolvedPlaceholders() {
        prompt.render(AmlWindow(accountId, from, until, listOf(aDomesticPayment()))) shouldNotContain "{{"
    }

    @Test
    fun hashesTheEvidencePaymentOverTheFactsTheDeciderSees() {
        val payment = aDomesticPayment()

        payment.contentHash shouldBe sha256Hex(
            "$paymentId|FPS|OUTBOUND|200000|GBP|2026-10-15T10:25:00Z|Bob Harrison|65-82-37|87654331||",
        )
        payment.contentHash shouldNotBe aDomesticPayment(amountMinorUnits = 200_001L).contentHash
    }

    private infix fun String.shouldContainLine(line: String) {
        lines().contains(line) shouldBe true
    }

    private fun aDomesticPayment(amountMinorUnits: Long = 200_000L) = aPayment(
        amountMinorUnits = amountMinorUnits,
        rail = "FPS",
        beneficiarySortCode = "65-82-37",
        beneficiaryAccountNo = "87654331",
    )

    private fun aSwiftPayment() = aPayment(
        amountMinorUnits = 200_000L,
        rail = "SWIFT",
        beneficiaryIban = "DE89370400440532017890",
        beneficiaryBic = "DEUTDEFFXXX",
    )

    private fun aPayment(
        amountMinorUnits: Long,
        rail: String,
        beneficiarySortCode: String? = null,
        beneficiaryAccountNo: String? = null,
        beneficiaryIban: String? = null,
        beneficiaryBic: String? = null,
    ) = MonitoredPayment(
        paymentId = paymentId,
        accountId = accountId,
        direction = "OUTBOUND",
        rail = rail,
        amountMinorUnits = amountMinorUnits,
        currency = "GBP",
        createdAt = Instant.parse("2026-10-15T10:25:00Z"),
        beneficiaryName = "Bob Harrison",
        beneficiarySortCode = beneficiarySortCode,
        beneficiaryAccountNo = beneficiaryAccountNo,
        beneficiaryIban = beneficiaryIban,
        beneficiaryBic = beneficiaryBic,
    )
}
