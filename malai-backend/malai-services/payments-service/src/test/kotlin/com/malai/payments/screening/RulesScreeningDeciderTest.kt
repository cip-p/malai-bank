package com.malai.payments.screening

import com.malai.commons.model.Currency
import com.malai.payments.entity.Rail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class RulesScreeningDeciderTest {

    private val properties = ScreeningProperties()
    private val decider = RulesScreeningDecider(properties, ScreeningWatchlist(properties))

    @Test
    fun honestlyNamesTheDeciderThatActuallyRan() {
        decider.modelId shouldBe "malai-screening-rules"
        decider.modelVersion shouldBe "1"
    }

    @Test
    fun approvesAnOrdinaryPaymentBelowTheThreshold() {
        val verdict = decide(amountMinorUnits = 2_500L)

        verdict.decision shouldBe ScreeningDecision.APPROVE
        verdict.reasons.shouldContainExactly()
        verdict.riskScore shouldBe 10
    }

    @Test
    fun approvesAPennyBelowTheThreshold() {
        decide(amountMinorUnits = 999_999L).decision shouldBe ScreeningDecision.APPROVE
    }

    @Test
    fun declinesExactlyAtTheThreshold() {
        val verdict = decide(amountMinorUnits = 1_000_000L)

        verdict.decision shouldBe ScreeningDecision.DECLINE
        verdict.reasons.shouldContainExactly("AMOUNT_AT_OR_ABOVE_THRESHOLD")
        verdict.riskScore shouldBe 80
    }

    @Test
    fun declinesAWatchlistCounterpartyAtAnyAmount() {
        val verdict = decide(amountMinorUnits = 100L, beneficiaryName = "Carol Singh")

        verdict.decision shouldBe ScreeningDecision.DECLINE
        verdict.reasons.shouldContainExactly("WATCHLIST_COUNTERPARTY")
        verdict.riskScore shouldBe 25
    }

    @Test
    fun matchesTheWatchlistIgnoringCaseAndSurroundingSpace() {
        decide(amountMinorUnits = 100L, beneficiaryName = "  carol singh ").decision shouldBe
            ScreeningDecision.DECLINE
    }

    @Test
    fun reportsBothReasonsWhenAWatchlistCounterpartyIsPaidAboveTheThreshold() {
        val verdict = decide(amountMinorUnits = 2_500_000L, beneficiaryName = "Carol Singh")

        verdict.decision shouldBe ScreeningDecision.DECLINE
        verdict.reasons.shouldContainExactly("AMOUNT_AT_OR_ABOVE_THRESHOLD", "WATCHLIST_COUNTERPARTY")
        verdict.riskScore shouldBe 95
    }

    @Test
    fun derivesRiskScoreFromTheAmountBand() {
        decide(amountMinorUnits = 99_999L).riskScore shouldBe 10
        decide(amountMinorUnits = 100_000L).riskScore shouldBe 25
        decide(amountMinorUnits = 499_999L).riskScore shouldBe 25
        decide(amountMinorUnits = 500_000L).riskScore shouldBe 45
        decide(amountMinorUnits = 999_999L).riskScore shouldBe 45
        decide(amountMinorUnits = 1_000_000L).riskScore shouldBe 80
    }

    @Test
    fun upliftsTheRiskScoreForAWatchlistHitWithoutExceedingNinetyNine() {
        decide(amountMinorUnits = 50L, beneficiaryName = "Carol Singh").riskScore shouldBe 25
        decide(amountMinorUnits = 5_000_000L, beneficiaryName = "Carol Singh").riskScore shouldBe 95
    }

    @Test
    fun timesOutOnTheArmedAmount() {
        shouldThrow<ScreeningTimedOutException> { decide(amountMinorUnits = 314_159L) }
    }

    @Test
    fun approvesAnUnnamedBeneficiaryBelowTheThreshold() {
        decide(amountMinorUnits = 2_500L, beneficiaryName = null).decision shouldBe ScreeningDecision.APPROVE
    }

    private fun decide(
        amountMinorUnits: Long,
        beneficiaryName: String? = "Bob Harrison",
    ): ScreeningVerdict =
        decider.decide(aSubject(amountMinorUnits, beneficiaryName), "a rendered prompt")

    private fun aSubject(amountMinorUnits: Long, beneficiaryName: String?) = ScreeningSubject(
        paymentId = UUID.fromString("00000000-0000-4000-8000-0000000000a1"),
        rail = Rail.FPS,
        amountMinorUnits = amountMinorUnits,
        currency = Currency.GBP,
        beneficiaryName = beneficiaryName,
        beneficiaryIdentifier = "65-82-37 ****4321",
        paymentPurpose = null,
        payerAccount = PayerAccountSnapshot(
            accountId = UUID.fromString("00000000-0000-4000-8000-0000000000b1"),
            name = "Personal",
            identifier = "65-82-37 ****0001",
            currency = Currency.GBP,
        ),
    )
}
