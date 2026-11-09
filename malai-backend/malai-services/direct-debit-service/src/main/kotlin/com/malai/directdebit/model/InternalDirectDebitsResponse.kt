package com.malai.directdebit.model

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import java.time.LocalDate
import java.util.UUID

data class InternalDirectDebitsResponse(
    val directDebits: List<InternalDirectDebitResponse>,
)

data class InternalDirectDebitResponse(
    val directDebitId: UUID,
    val accountId: UUID,
    val creditorName: String,
    val amountMinorUnits: Long?,
    val currency: Currency,
    val frequency: Frequency,
    val status: InstrumentStatus,
    val startDate: LocalDate,
)
