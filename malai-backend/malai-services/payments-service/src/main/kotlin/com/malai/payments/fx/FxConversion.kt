package com.malai.payments.fx

import com.malai.commons.model.Currency

data class FxConversion(
    val amountMinorUnits: Long,
    val currency: Currency,
    val rateUsed: String,
)
