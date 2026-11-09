package com.malai.test.e2e

import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import java.util.UUID

/**
 * E2E direct-database helpers for the card-service tables (`card_authorisations`, `card_refunds`).
 * The harness has no in-process Spring context, so the card lifecycle transitions that resolve
 * asynchronously — the queue-borne presentment (AUTHORISED -> SETTLED) and refund — are asserted
 * by polling Postgres over JDBC. Mirrors [WithPaymentsDb]'s shape.
 *
 * The synchronous authorise leg is already committed by the time the purchase HTTP call returns,
 * so a status read for AUTHORISED needs no wait; SETTLED (after `present now` rides the scheme
 * presentment queue into card-service) and the refund credit do, hence the polling defaults.
 */
interface WithCardsDb {

    /**
     * Block until the authorisation identified by its scheme reference reaches [expectedStatus]
     * (AUTHORISED / SETTLED / DECLINED / REVERSED), or fail with the last-seen status.
     */
    fun theCardAuthorisationReachesStatusInTheDatabase(
        schemeReference: UUID,
        expectedStatus: String,
        timeoutMs: Long = 30_000L,
        pollIntervalMs: Long = 250L,
    ) = CallableAction {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSeen: String? = null
        while (System.currentTimeMillis() < deadline) {
            lastSeen = readAuthorisation(schemeReference)?.status
            if (lastSeen == expectedStatus) return@CallableAction
            Thread.sleep(pollIntervalMs)
        }
        error(
            "Card authorisation for scheme reference $schemeReference did not reach status " +
                "'$expectedStatus' within ${timeoutMs}ms (last seen: $lastSeen)"
        )
    }

    /**
     * Block until a refund row exists for [authorisationId], or fail. Use after a sandbox refund
     * to wait out the (stub -> card-gateway -> cards-scheme-event-refunded -> card-service) round
     * trip that posts the merchant-named credit.
     */
    fun theCardRefundForAuthorisationReachesTheDatabase(
        authorisationId: UUID,
        timeoutMs: Long = 30_000L,
        pollIntervalMs: Long = 250L,
    ) = CallableAction {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (refundExists(authorisationId)) return@CallableAction
            Thread.sleep(pollIntervalMs)
        }
        error("No refund for authorisation $authorisationId appeared within ${timeoutMs}ms")
    }

    /**
     * Poll until the authorisation for [schemeReference] is present and SETTLED, then return its
     * card-service authorisation id — the canonical purchase id the sandbox refund trigger takes.
     * The sandbox purchase response only carries the scheme reference, so the settled purchase's
     * authorisation id is resolved here (matching card-service's server-side resolution).
     */
    fun theSettledAuthorisationIdForSchemeReference(
        schemeReference: UUID,
        timeoutMs: Long = 30_000L,
        pollIntervalMs: Long = 250L,
    ): UUID {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSeen: String? = null
        while (System.currentTimeMillis() < deadline) {
            val authorisation = readAuthorisation(schemeReference)
            lastSeen = authorisation?.status
            if (authorisation != null && authorisation.status == "SETTLED") return authorisation.authorisationId
            Thread.sleep(pollIntervalMs)
        }
        error(
            "Authorisation for scheme reference $schemeReference was not SETTLED within " +
                "${timeoutMs}ms (last seen: $lastSeen)"
        )
    }

    private data class AuthorisationRow(val authorisationId: UUID, val status: String)

    private fun readAuthorisation(schemeReference: UUID): AuthorisationRow? =
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.prepareStatement(
                "SELECT authorisation_id, status FROM card_authorisations WHERE scheme_reference = ?"
            ).use { ps ->
                ps.setObject(1, schemeReference)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    AuthorisationRow(UUID.fromString(rs.getString("authorisation_id")), rs.getString("status"))
                } else {
                    null
                }
            }
        }

    private fun refundExists(authorisationId: UUID): Boolean =
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM card_refunds WHERE authorisation_id = ?"
            ).use { ps ->
                ps.setObject(1, authorisationId)
                ps.executeQuery().next()
            }
        }
}
