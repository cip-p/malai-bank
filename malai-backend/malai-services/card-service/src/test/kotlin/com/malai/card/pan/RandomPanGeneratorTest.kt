package com.malai.card.pan

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class RandomPanGeneratorTest {

    private val generator = RandomPanGenerator()

    @Test
    fun `generates a sixteen digit pan`() {
        generator.generatePan() shouldMatch Regex("\\d{16}")
    }

    @Test
    fun `generates a pan from the malai bin`() {
        generator.generatePan() shouldStartWith Pan.MALAI_BIN
    }

    @Test
    fun `generates a Luhn-valid pan`() {
        Luhn.isValid(generator.generatePan()) shouldBe true
    }

    @Test
    fun `generates unique pans across many generations`() {
        val pans = (1..1000).map { generator.generatePan() }

        pans.toSet() shouldHaveSize pans.size
    }

    @Test
    fun `generates a three digit cvv`() {
        generator.generateCvv() shouldMatch Regex("\\d{3}")
    }
}
