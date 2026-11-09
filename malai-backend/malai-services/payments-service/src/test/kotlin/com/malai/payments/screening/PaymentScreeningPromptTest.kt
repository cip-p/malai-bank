package com.malai.payments.screening

import com.malai.client.malai.accounts.lookup.model.InternalAccountResponse
import com.malai.commons.model.Currency
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PaymentScreeningPromptTest {

    private val properties = ScreeningProperties()
    private val watchlist = ScreeningWatchlist(properties)
    private val prompt = PaymentScreeningPrompt(watchlist)

    private val paymentId = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
    private val payerAccountId = UUID.fromString("00000000-0000-4000-8000-0000000000b1")

    @Test
    fun pinsTheTemplateByIdVersionAndGenuineContentHash() {
        prompt.templateId shouldBe "payment-screening"
        prompt.templateVersion shouldBe "v1"
        prompt.templateHash shouldBe sha256Hex(
            javaClass.getResourceAsStream("/screening/payment-screening-v1.txt")!!
                .readBytes().toString(Charsets.UTF_8),
        )
    }

    @Test
    fun rendersTheDomesticPaymentsFactsAndThePayerSnapshot() {
        val rendered = prompt.render(ScreeningSubject.of(anFpsPayment(), aPayerAccount()))

        rendered shouldBe """
            |You are the outbound payment screening assistant for Malai Bank.
            |
            |Assess the payment below for financial-crime risk. Weigh the amount against the
            |bank's referral threshold, and check the beneficiary against the sanctions and
            |financial-crime watchlist supplied as context. You are given no balances and no
            |history: judge only the facts presented here.
            |
            |PAYMENT
            |  id: $paymentId
            |  rail: FPS
            |  amount: 2500 minor units of GBP
            |  beneficiary name: Bob Harrison
            |  beneficiary identifier: 65-82-37 ****4321
            |  purpose: not supplied
            |
            |PAYER ACCOUNT
            |  id: $payerAccountId
            |  name: Personal
            |  identifier: 65-82-37 ****0001
            |  currency: GBP
            |
            |WATCHLIST
            |  version: 2026-07-01
            |  entries: Carol Singh
            |
            |Respond with a single JSON object and nothing else:
            |{"decision": "APPROVE" | "DECLINE", "riskScore": <integer 0-100>, "reasons": [<string>, ...]}
            |
        """.trimMargin()
    }

    @Test
    fun rendersTheInternationalPurposeAndMaskedIban() {
        val rendered = prompt.render(ScreeningSubject.of(aSwiftPayment(), aPayerAccount()))

        rendered shouldContainLine "  rail: SWIFT"
        rendered shouldContainLine "  beneficiary identifier: DEUTDEFFXXX ******************7890"
        rendered shouldContainLine "  purpose: Property purchase"
    }

    @Test
    fun neverExposesTheFullBeneficiaryOrPayerAccountNumber() {
        val rendered = prompt.render(ScreeningSubject.of(anFpsPayment(), aPayerAccount()))

        rendered shouldNotContain "87654321"
        rendered shouldNotContain "10000001"
    }

    @Test
    fun leavesNoUnresolvedPlaceholders() {
        prompt.render(ScreeningSubject.of(anFpsPayment(), aPayerAccount())) shouldNotContain "{{"
    }

    @Test
    fun hashesThePayerSnapshotOverTheLookupActuallyUsed() {
        val snapshot = PayerAccountSnapshot.of(aPayerAccount())

        snapshot.contentHash shouldBe sha256Hex("$payerAccountId|Personal|65-82-37 ****0001|GBP")
        snapshot.contentHash shouldNotBe PayerAccountSnapshot.of(aPayerAccount(name = "Joint")).contentHash
    }

    @Test
    fun pinsTheWatchlistByVersionAndContentHash() {
        watchlist.version shouldBe "2026-07-01"
        watchlist.contentHash shouldBe sha256Hex("2026-07-01\nCarol Singh")
        watchlist.contentHash shouldNotBe
            ScreeningWatchlist(
                properties.copy(watchlist = ScreeningProperties.Watchlist(names = listOf("Carol Singh", "Dan Patel"))),
            ).contentHash
    }

    private infix fun String.shouldContainLine(line: String) {
        lines().contains(line) shouldBe true
    }

    private fun aPayerAccount(name: String = "Personal") = InternalAccountResponse(
        accountId = payerAccountId,
        accountHolderId = UUID.fromString("00000000-0000-4000-8000-0000000000c1"),
        currency = "GBP",
        sortCode = "65-82-37",
        accountNumber = "10000001",
        name = name,
    )

    private fun anFpsPayment() = aPayment(
        rail = Rail.FPS,
        beneficiarySortCode = "65-82-37",
        beneficiaryAccountNo = "87654321",
    )

    private fun aSwiftPayment() = aPayment(
        rail = Rail.SWIFT,
        beneficiaryIban = "DE89370400440532017890",
        beneficiaryBic = "DEUTDEFFXXX",
        paymentPurpose = "Property purchase",
    )

    private fun aPayment(
        rail: Rail,
        beneficiarySortCode: String? = null,
        beneficiaryAccountNo: String? = null,
        beneficiaryIban: String? = null,
        beneficiaryBic: String? = null,
        paymentPurpose: String? = null,
    ) = PaymentEntity(
        paymentId = paymentId,
        rail = rail,
        direction = PaymentDirection.OUTBOUND,
        state = PaymentState.PENDING,
        debtorAccountId = payerAccountId,
        beneficiarySortCode = beneficiarySortCode,
        beneficiaryAccountNo = beneficiaryAccountNo,
        beneficiaryName = "Bob Harrison",
        beneficiaryIban = beneficiaryIban,
        beneficiaryBic = beneficiaryBic,
        paymentPurpose = paymentPurpose,
        amountMinorUnits = 2_500L,
        currency = Currency.GBP,
        endToEndId = UUID.fromString("00000000-0000-4000-8000-0000000000d1"),
        createdAt = Instant.parse("2026-10-15T10:30:00Z"),
    )
}
