package com.malai.directdebit.model

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.directdebit.entity.DirectDebitEntity
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class DirectDebitsResponse(
    val directDebits: List<DirectDebitResponse>,
)

data class DirectDebitResponse(
    val directDebitId: UUID,
    val accountId: UUID,
    val creditorName: String,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val reference: String?,
    val amountMinorUnits: Long?,
    val currency: Currency,
    val frequency: Frequency,
    val status: InstrumentStatus,
    val startDate: LocalDate,
    val cancelledAt: Instant?,
    val nextCollectionAt: Instant?,
)

fun DirectDebitEntity.toDirectDebitResponse() = DirectDebitResponse(
    directDebitId = directDebitId,
    accountId = accountId,
    creditorName = creditorName,
    creditorSortCode = creditorSortCode,
    creditorAccountNumber = creditorAccountNumber,
    reference = reference,
    amountMinorUnits = amountMinorUnits,
    currency = currency,
    frequency = frequency,
    status = status,
    startDate = startDate,
    cancelledAt = cancelledAt,
    nextCollectionAt = nextCollectionAt,
)
