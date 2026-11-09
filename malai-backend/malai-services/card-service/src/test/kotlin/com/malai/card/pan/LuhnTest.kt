package com.malai.card.pan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LuhnTest {

    @Test
    fun `accepts a known Luhn-valid number`() {
        Luhn.isValid("4539148803436467") shouldBe true
    }

    @Test
    fun `rejects a number whose check digit is wrong`() {
        Luhn.isValid("4539148803436460") shouldBe false
    }

    @Test
    fun `rejects a non-numeric string`() {
        Luhn.isValid("4539abcd03436467") shouldBe false
    }

    @Test
    fun `rejects an empty string`() {
        Luhn.isValid("") shouldBe false
    }

    @Test
    fun `check digit completes a number so that it is Luhn-valid`() {
        val body = "753118980000000"

        val complete = body + Luhn.checkDigit(body)

        Luhn.isValid(complete) shouldBe true
    }
}
