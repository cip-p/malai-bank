package com.malai.client.malai.directdebit.model

import java.util.UUID

/**
 * Wire contract for the sandbox mandate-lodgement round trip against
 * direct-debit-service's internal endpoint (`POST /api/v1/internal/direct-debits`).
 *
 * Dates cross the wire as strings (`startDate` as an ISO local date, the timestamps
 * as ISO instants). [AbstractMalaiClient][com.malai.client.malai.AbstractMalaiClient]
 * serialises with a plain `jacksonObjectMapper()` that has no `jsr310` module, so
 * `java.time` values are deliberately kept off this contract — the direct-debit
 * controller (Spring MVC, jsr310 present) parses/renders them on its side.
 */
data class LodgeMandateRequest(
    val accountId: UUID,
    val creditorName: String,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val reference: String?,
    val amountMinorUnits: Long,
    val currency: String,
    val frequency: String,
    val startDate: String,
)

data class LodgedMandateResponse(
    val directDebitId: UUID,
    val accountId: UUID,
    val creditorName: String,
    val creditorSortCode: String,
    val creditorAccountNumber: String,
    val reference: String?,
    val amountMinorUnits: Long?,
    val currency: String,
    val frequency: String,
    val status: String,
    val startDate: String,
    val cancelledAt: String?,
    val nextCollectionAt: String?,
)
