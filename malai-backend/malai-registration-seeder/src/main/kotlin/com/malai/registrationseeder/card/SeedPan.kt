package com.malai.registrationseeder.card

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

object SeedPan {

    const val MALAI_BIN = "990432"
    const val PAN_LENGTH = 16
    private const val EXPIRY_YEARS = 3L
    private val EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MM/yy")

    fun generatePan(random: Random): String {
        val withoutCheckDigit = buildString {
            append(MALAI_BIN)
            repeat(PAN_LENGTH - MALAI_BIN.length - 1) { append(random.nextInt(10)) }
        }
        return withoutCheckDigit + checkDigit(withoutCheckDigit)
    }

    fun generateCvv(random: Random): String = "%03d".format(random.nextInt(1000))

    fun expiryFor(issuedAt: Instant): String =
        issuedAt.atZone(ZoneOffset.UTC).toLocalDate().plusYears(EXPIRY_YEARS).format(EXPIRY_FORMAT)

    fun mask(pan: String): String = "*".repeat(pan.length - 4) + pan.takeLast(4)

    private fun checkDigit(withoutCheckDigit: String): Int {
        val checksum = checksum(withoutCheckDigit + "0")
        return (10 - checksum % 10) % 10
    }

    private fun checksum(number: String): Int {
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
        return sum
    }
}
