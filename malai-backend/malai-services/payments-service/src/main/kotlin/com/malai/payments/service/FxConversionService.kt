package com.malai.payments.service

import com.malai.commons.model.Currency
import com.malai.payments.fx.FxConversion
import com.malai.payments.fx.StubFxRatesProperties
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class FxConversionService(
    private val rates: StubFxRatesProperties,
) {

    fun convert(sourceCurrency: Currency, targetCurrency: Currency, amountMinorUnits: Long): FxConversion {
        val rate = lookupRate(sourceCurrency, targetCurrency)
            ?: error("no FX rate configured for $sourceCurrency -> $targetCurrency")
        val target = BigDecimal.valueOf(amountMinorUnits)
            .multiply(rate)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        return FxConversion(
            amountMinorUnits = target,
            currency = targetCurrency,
            rateUsed = rate.stripTrailingZeros().toPlainString(),
        )
    }

    fun supports(sourceCurrency: Currency, targetCurrency: Currency): Boolean =
        lookupRate(sourceCurrency, targetCurrency) != null

    private fun lookupRate(sourceCurrency: Currency, targetCurrency: Currency): BigDecimal? {
        val key = "${sourceCurrency.name}_${targetCurrency.name}"
        return rates.rates[key]?.let { BigDecimal(it) }
    }
}

