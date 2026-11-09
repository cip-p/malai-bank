package com.malai.registrationseeder.ledger

import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.Currency
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Writes one `transactions` row + two balanced `ledger_entries` rows + one
 * SEED_TRANSACTION audit event. The signed [amountOnCurrentSide] lands on [currentSideAccountId]
 * and its negation lands on [counterpartyAccountId], so the V5 `ledger_entries_sum_to_zero`
 * deferred trigger sees a sum of zero at COMMIT.
 *
 * The ledger is now its own service; this dev-only seeder writes the ledger tables directly via
 * raw SQL (it no longer maps account-service JPA entities). JdbcTemplate is transaction-aware, so
 * the inserts run inside the caller's `@Transactional` and the deferred trigger validates every
 * pair as one commit.
 */
@Component
class LedgerSeedHelper(
    private val jdbcTemplate: JdbcTemplate,
    private val auditWriter: AuditWriter,
    private val uuidGenerator: UuidGenerator,
) {

    fun postBalancedPair(
        postedAt: Instant,
        currentSideAccountId: UUID,
        counterpartyAccountId: UUID,
        counterpartyName: String,
        amountOnCurrentSide: Long,
        description: String,
        type: String,
        initiatedById: UUID,
        idGenerator: UuidGenerator? = null,
    ) {
        val ids = idGenerator ?: uuidGenerator
        val transactionId = ids.generate()
        val postedTimestamp = Timestamp.from(postedAt)
        jdbcTemplate.update(
            "INSERT INTO transactions (transaction_id, initiated_by_id, initiated_at, type, description) " +
                "VALUES (?, ?, ?, ?, ?)",
        ) { ps ->
            ps.setObject(1, transactionId)
            ps.setObject(2, initiatedById)
            ps.setTimestamp(3, postedTimestamp)
            ps.setString(4, type)
            ps.setString(5, description)
        }
        insertEntry(ids, transactionId, currentSideAccountId, amountOnCurrentSide, postedTimestamp, description, counterpartyAccountId, counterpartyName)
        insertEntry(ids, transactionId, counterpartyAccountId, -amountOnCurrentSide, postedTimestamp, description, currentSideAccountId, counterpartyName)
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_TRANSACTION",
                resourceType = "Transaction",
                resourceId = transactionId.toString(),
                afterState = mapOf(
                    "amountMinorUnits" to amountOnCurrentSide,
                    "currency" to Currency.GBP.name,
                    "counterparty" to counterpartyName,
                    "description" to description,
                ),
            )
        )
    }

    fun countEntriesForAccount(accountId: UUID): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            accountId,
        ) ?: 0L

    /**
     * Writes one PLACED `holds` row directly, reserving [amountMinorUnits] on [accountId] so its
     * Available balance drops (available = ledger − Σ PLACED holds) without moving the Ledger
     * balance — the shape a live, unsettled card authorisation leaves behind. Backdating [placedAt]
     * lets the seeder plant a pending authorisation that reads as if it landed before "now"; the
     * real ledger-service placeHold stamps `timeService.now()` and cannot. No audit event: a hold is
     * a balance projection, not an audited domain event (the ledger-service placeHold audits nothing
     * either).
     */
    fun placeHold(holdId: UUID, accountId: UUID, amountMinorUnits: Long, placedAt: Instant) {
        jdbcTemplate.update(
            "INSERT INTO holds (hold_id, account_id, amount_minor_units, status, placed_at) " +
                "VALUES (?, ?, ?, 'PLACED', ?)",
        ) { ps ->
            ps.setObject(1, holdId)
            ps.setObject(2, accountId)
            ps.setLong(3, amountMinorUnits)
            ps.setTimestamp(4, Timestamp.from(placedAt))
        }
    }

    private fun insertEntry(
        ids: UuidGenerator,
        transactionId: UUID,
        accountId: UUID,
        amountMinorUnits: Long,
        postedAt: Timestamp,
        description: String,
        counterpartyId: UUID,
        merchantName: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO ledger_entries " +
                "(entry_id, transaction_id, account_id, amount_minor_units, posted_at, description, counterparty_id, merchant_name) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        ) { ps ->
            ps.setObject(1, ids.generate())
            ps.setObject(2, transactionId)
            ps.setObject(3, accountId)
            ps.setLong(4, amountMinorUnits)
            ps.setTimestamp(5, postedAt)
            ps.setString(6, description)
            ps.setObject(7, counterpartyId)
            ps.setString(8, merchantName)
        }
    }
}
