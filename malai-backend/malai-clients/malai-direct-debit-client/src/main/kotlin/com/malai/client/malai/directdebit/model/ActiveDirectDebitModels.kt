package com.malai.client.malai.directdebit.model

import java.util.UUID

/**
 * Wire contract for the per-account active-mandates read against direct-debit-service's internal
 * endpoint (`GET /api/v1/internal/direct-debits?accountId=…`), consumed by account-service when
 * composing the balance forecast. Twin of the standing-order client's `ActiveStandingOrdersResponse`.
 *
 * `amountMinorUnits` is nullable to mirror the mandate schema (variable mandates carry no fixed
 * amount), though in v1 every active mandate has a populated amount; account-service drops any
 * amount-less mandate rather than forecast an unknown outflow.
 */
data class ActiveDirectDebitsResponse(
    val directDebits: List<ActiveDirectDebit>,
)

data class ActiveDirectDebit(
    val directDebitId: UUID,
    val accountId: UUID,
    val creditorName: String,
    val amountMinorUnits: Long?,
    val currency: String,
    val frequency: String,
    val status: String,
    val startDate: String,
)
