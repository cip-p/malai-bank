package com.malai.accounts.identifier

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IbanCalculatorTest {

    /**
     * Reference IBAN published in the IBAN spec wiki / ISO 13616 examples.
     * Bank: NatWest (`NWBK`), sort code `60-16-13`, account `31926819`
     *  -> `GB29 NWBK 6016 1331 9268 19`
     */
    @Test
    fun `computes the canonical NatWest example`() {
        IbanCalculator.ukIban(
            bic = "NWBKGB2L",
            sortCode = "60-16-13",
            accountNumber = "31926819",
        ) shouldBe "GB29NWBK60161331926819"
    }

    @Test
    fun `accepts undashed sort code`() {
        IbanCalculator.ukIban(
            bic = "NWBKGB2L",
            sortCode = "601613",
            accountNumber = "31926819",
        ) shouldBe "GB29NWBK60161331926819"
    }

    @Test
    fun `lowercases letters in the bank code`() {
        IbanCalculator.ukIban(
            bic = "nwbkgb2l",
            sortCode = "60-16-13",
            accountNumber = "31926819",
        ) shouldBe "GB29NWBK60161331926819"
    }
}
