package com.malai.directdebit.model

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import java.time.LocalDate
import java.util.UUID

/**
 * Service-to-service mandate lodgement command received on the internal endpoint. The caller
 * (sandbox-service) has already resolved and ownership-checked the account and snapshotted the
 * creditor from the counterparty directory; this endpoint owns the authoritative
 * start-date-in-the-past guard, the ACTIVE row write, and the audit event.
 */
data class LodgeMandateRequest(
    val accountId: UUID,
    val creditorName: String,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val reference: String?,
    val amountMinorUnits: Long,
    val currency: Currency,
    val frequency: Frequency,
    val startDate: LocalDate,
)
