package com.malai.auth.service

import com.malai.test.commons.support.StubbedTimeService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.Random

internal class GeneratedDetailsGeneratorTest {

    private val timeService = StubbedTimeService().apply {
        givenNowIs(Instant.parse("2026-06-10T10:30:00Z"))
    }

    @Test
    fun `generates a plausible UK address`() {
        val generator = GeneratedDetailsGenerator(timeService, Random(7))

        repeat(200) {
            val address = generator.generate().address

            assertThat(address.line1).isNotBlank()
            assertThat(address.city).isNotBlank()
            assertThat(address.postcode).matches("^[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}$")
            assertThat(address.country).isEqualTo("GB")
        }
    }

    @Test
    fun `generates an adult date of birth between 25 and 65 years old`() {
        val generator = GeneratedDetailsGenerator(timeService, Random(7))

        repeat(200) {
            val today = LocalDate.parse("2026-06-10")
            val age = Period.between(generator.generate().dateOfBirth, today).years

            assertThat(age).isBetween(25, 65)
        }
    }

    @Test
    fun `is deterministic under a stubbed random source`() {
        val first = GeneratedDetailsGenerator(timeService, Random(42)).generate()
        val second = GeneratedDetailsGenerator(timeService, Random(42)).generate()

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `draws varied details across registrations`() {
        val generator = GeneratedDetailsGenerator(timeService, Random(42))

        val details = (0 until 50).map { generator.generate() }

        assertThat(details.map { it.address }.toSet().size).isGreaterThan(1)
        assertThat(details.map { it.dateOfBirth }.toSet().size).isGreaterThan(1)
    }
}
