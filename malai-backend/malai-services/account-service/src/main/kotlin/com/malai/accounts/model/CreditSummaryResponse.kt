package com.malai.accounts.model

import com.malai.commons.model.Currency
import java.time.LocalDate

data class CreditSummaryResponse(
    val currency: Currency,
    val creditLimitMinorUnits: Long,
    val owedMinorUnits: Long,
    val availableCreditMinorUnits: Long,
    val statementBalanceMinorUnits: Long? = null,
    val minimumPaymentMinorUnits: Long? = null,
    val dueDate: LocalDate? = null,
)
