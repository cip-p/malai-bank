package com.malai.client.malai.directdebit

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

/**
 * WireMock stub for direct-debit-service's internal mandate-lodgement endpoint. Mixed into the
 * acceptance tests of any service that lodges a mandate over the boundary (sandbox-service), so
 * the outbound call can be intercepted and its request shape asserted, and into the client's own
 * request-construction test.
 *
 * Dates are stubbed as strings, matching the wire contract in
 * [com.malai.client.malai.directdebit.model.LodgeMandateModels].
 */
interface WithDirectDebitLodgement : WithWiremock {

    fun lodgeMandateReturns(
        directDebitId: UUID,
        accountId: UUID,
        creditorName: String = "British Gas",
        creditorSortCode: String = "20-00-00",
        creditorAccountNumber: String = "12345678",
        reference: String? = null,
        amountMinorUnits: Long = 4500,
        currency: String = "GBP",
        frequency: String = "MONTHLY",
        status: String = "ACTIVE",
        startDate: String = "2026-06-01",
        cancelledAt: String? = null,
        nextCollectionAt: String? = "2026-06-01T08:00:00Z",
    ) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/direct-debits"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            body(
                                directDebitId, accountId, creditorName, creditorSortCode,
                                creditorAccountNumber, reference, amountMinorUnits, currency,
                                frequency, status, startDate, cancelledAt, nextCollectionAt,
                            )
                        )
                )
        )
    }

    private fun body(
        directDebitId: UUID,
        accountId: UUID,
        creditorName: String,
        creditorSortCode: String,
        creditorAccountNumber: String,
        reference: String?,
        amountMinorUnits: Long,
        currency: String,
        frequency: String,
        status: String,
        startDate: String,
        cancelledAt: String?,
        nextCollectionAt: String?,
    ): String =
        """{"directDebitId":"$directDebitId","accountId":"$accountId","creditorName":"$creditorName","creditorSortCode":"$creditorSortCode","creditorAccountNumber":"$creditorAccountNumber","reference":${jsonOrNull(reference)},"amountMinorUnits":$amountMinorUnits,"currency":"$currency","frequency":"$frequency","status":"$status","startDate":"$startDate","cancelledAt":${jsonOrNull(cancelledAt)},"nextCollectionAt":${jsonOrNull(nextCollectionAt)}}"""

    private fun jsonOrNull(value: String?): String = if (value == null) "null" else "\"$value\""

    companion object {
        const val CONTEXT_PATH = "/direct-debit-service"
    }
}
