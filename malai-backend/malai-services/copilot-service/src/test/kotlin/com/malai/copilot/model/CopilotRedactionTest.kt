package com.malai.copilot.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CopilotRedactionTest {

    @Test
    fun anAccountNumberInAnAuditStateIsMaskedToItsLastFour() {
        maskedAuditState(mapOf("toAccountNumber" to "11223344")) shouldBe
            mapOf("toAccountNumber" to "****3344")
    }

    @Test
    fun anIbanInAnAuditStateIsMaskedToItsLastFour() {
        maskedAuditState(mapOf("toIban" to "GB33BUKB20201555555555")) shouldBe
            mapOf("toIban" to "******************5555")
    }

    @Test
    fun aSortCodeIsShownInFullBecauseItNamesABankRatherThanACustomer() {
        maskedAuditState(mapOf("toSortCode" to "30-00-03")) shouldBe
            mapOf("toSortCode" to "30-00-03")
    }

    @Test
    fun theFactsThatExplainThePaymentAreLeftUntouched() {
        val afterState = mapOf(
            "state" to "REFERRED",
            "rail" to "FPS",
            "amountMinorUnits" to 2_500L,
            "currency" to "GBP",
            "screeningDecisionId" to "4043092c-ddd5-4d03-9373-748a292947c2",
        )

        maskedAuditState(afterState) shouldBe afterState
    }

    @Test
    fun anAbsentAccountNumberStaysAbsentRatherThanBecomingAMaskThatImpliesOne() {
        maskedAuditState(mapOf("toAccountNumber" to "")) shouldBe mapOf("toAccountNumber" to "")
    }

    @Test
    fun anAccountNumberIsMaskedWhateverTheWriterCalledIt() {
        maskedAuditState(
            mapOf(
                "beneficiaryAccountNumber" to "11223344",
                "debtorAccountNo" to "55667788",
                "creditorIBAN" to "GB33BUKB20201555555555",
            ),
        ) shouldBe mapOf(
            "beneficiaryAccountNumber" to "****3344",
            "debtorAccountNo" to "****7788",
            "creditorIBAN" to "******************5555",
        )
    }

    @Test
    fun anInternalAccountIdIsNotAnAccountNumberAndIsLeftAlone() {
        maskedAuditState(mapOf("fromAccountId" to "81a0f567-4a12-4b48-a2a3-f8eee97c0172")) shouldBe
            mapOf("fromAccountId" to "81a0f567-4a12-4b48-a2a3-f8eee97c0172")
    }
}
