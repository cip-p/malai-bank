package com.malai.card.pan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class PanTest {

    @Test
    fun `masks all but the last four digits`() {
        Pan.mask("9904320000012341") shouldBe "************2341"
    }

    @Test
    fun `masked form keeps the same length as the pan`() {
        Pan.mask("9904320000012341").length shouldBe 16
    }

    @Test
    fun `expiry is three years after issuance in MM slash YY form`() {
        Pan.expiryFor(Instant.parse("2026-10-15T10:30:00Z")) shouldBe "10/29"
    }

    @Test
    fun `expiry preserves the month at the year boundary`() {
        Pan.expiryFor(Instant.parse("2026-12-31T23:00:00Z")) shouldBe "12/29"
    }

    @Test
    fun `the malai bin is in the unassigned nine series`() {
        Pan.MALAI_BIN.first() shouldBe '9'
    }
}
