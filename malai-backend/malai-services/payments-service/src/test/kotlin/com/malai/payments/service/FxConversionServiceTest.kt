package com.malai.payments.service

import com.malai.commons.model.Currency
import com.malai.payments.fx.StubFxRatesProperties
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FxConversionServiceTest {

    private val service = FxConversionService(
        StubFxRatesProperties(
            rates = mapOf(
                "GBP_EUR" to "1.15",
                "GBP_USD" to "1.25",
            ),
        ),
    )

    @Test
    fun convertsGbpToEur() {
        val result = service.convert(Currency.GBP, Currency.EUR, 10_000L)
        result.amountMinorUnits shouldBe 11_500L
        result.currency shouldBe Currency.EUR
        result.rateUsed shouldBe "1.15"
    }

    @Test
    fun convertsGbpToUsd() {
        val result = service.convert(Currency.GBP, Currency.USD, 8_000L)
        result.amountMinorUnits shouldBe 10_000L
        result.currency shouldBe Currency.USD
        result.rateUsed shouldBe "1.25"
    }

    @Test
    fun supportsReturnsTrueForKnownPair() {
        service.supports(Currency.GBP, Currency.EUR) shouldBe true
    }

    @Test
    fun supportsReturnsFalseForUnknownPair() {
        service.supports(Currency.GBP, Currency.JPY) shouldBe false
    }
}
