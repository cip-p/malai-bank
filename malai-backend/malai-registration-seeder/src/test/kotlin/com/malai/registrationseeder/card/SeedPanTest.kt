package com.malai.registrationseeder.card

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random

class SeedPanTest {

    @Test
    fun generatesA16DigitLuhnValidPanFromTheMalaiBin() {
        val pan = SeedPan.generatePan(Random(1))

        assertThat(pan).hasSize(16).startsWith("990432")
        assertThat(pan.all { it.isDigit() }).isTrue()
        assertThat(luhnValid(pan)).isTrue()
    }

    @Test
    fun reproducesTheSamePanForTheSameSeed() {
        assertThat(SeedPan.generatePan(Random(42))).isEqualTo(SeedPan.generatePan(Random(42)))
    }

    @Test
    fun differentSeedsProduceDifferentPans() {
        assertThat(SeedPan.generatePan(Random(1))).isNotEqualTo(SeedPan.generatePan(Random(2)))
    }

    @Test
    fun generatesAThreeDigitCvv() {
        assertThat(SeedPan.generateCvv(Random(1))).matches("\\d{3}")
    }

    @Test
    fun expiryIsThreeYearsOutInMmYy() {
        assertThat(SeedPan.expiryFor(Instant.parse("2026-07-12T00:00:00Z"))).isEqualTo("07/29")
    }

    @Test
    fun masksToTheLastFour() {
        assertThat(SeedPan.mask("9904320000012341")).isEqualTo("************2341")
    }

    private fun luhnValid(number: String): Boolean {
        var sum = 0
        val reversed = number.reversed()
        for (index in reversed.indices) {
            var digit = reversed[index] - '0'
            if (index % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return sum % 10 == 0
    }
}
