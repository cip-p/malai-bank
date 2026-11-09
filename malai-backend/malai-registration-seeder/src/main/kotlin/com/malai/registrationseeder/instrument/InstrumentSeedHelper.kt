package com.malai.registrationseeder.instrument

import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Writes a saved payee, an ACTIVE standing order, or an ACTIVE fixed-amount
 * direct-debit mandate for a freshly-registered user, plus a matching
 * SEED_*_CREATED audit event — the instrument counterpart of [com.malai.registrationseeder.ledger.LedgerSeedHelper].
 *
 * Like the ledger helper this writes the tables directly via raw SQL rather than
 * pulling the standing-order / direct-debit service configurations into the
 * registration-seeder context: those configurations component-scan their whole
 * package, which would import the per-minute executor / requester schedulers and
 * their scheme clients into every host that seeds a user. JdbcTemplate is
 * transaction-aware, so the inserts run inside the caller's `@Transactional` and
 * the payee row is visible to the standing-order foreign key in the same commit.
 */
@Component
class InstrumentSeedHelper(
    private val jdbcTemplate: JdbcTemplate,
    private val auditWriter: AuditWriter,
    private val uuidGenerator: UuidGenerator,
) {

    fun insertPayee(
        accountHolderId: UUID,
        name: String,
        sortCode: String,
        accountNumber: String,
        nickname: String,
        createdAt: Instant,
        createdBy: UUID,
        idGenerator: UuidGenerator? = null,
    ): UUID {
        val payeeId = (idGenerator ?: uuidGenerator).generate()
        jdbcTemplate.update(
            "INSERT INTO payees " +
                "(payee_id, account_holder_id, name, sort_code, account_number, nickname, created_at, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        ) { ps ->
            ps.setObject(1, payeeId)
            ps.setObject(2, accountHolderId)
            ps.setString(3, name)
            ps.setString(4, sortCode)
            ps.setString(5, accountNumber)
            ps.setString(6, nickname)
            ps.setTimestamp(7, Timestamp.from(createdAt))
            ps.setObject(8, createdBy)
        }
        recordAudit(createdBy, "SEED_PAYEE_CREATED", "Payee", payeeId, mapOf("name" to name, "sortCode" to sortCode))
        return payeeId
    }

    fun insertStandingOrder(
        accountId: UUID,
        payeeId: UUID,
        payeeName: String,
        payeeSortCode: String,
        payeeAccountNumber: String,
        reference: String,
        amountMinorUnits: Long,
        frequency: Frequency,
        startDate: LocalDate,
        nextPaymentAt: Instant,
        createdAt: Instant,
        createdBy: UUID,
        idGenerator: UuidGenerator? = null,
    ): UUID {
        val standingOrderId = (idGenerator ?: uuidGenerator).generate()
        jdbcTemplate.update(
            "INSERT INTO standing_orders " +
                "(standing_order_id, account_id, payee_id, payee_name, payee_sort_code, payee_account_number, " +
                "reference, amount_minor_units, currency, frequency, status, start_date, next_payment_at, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ) { ps ->
            ps.setObject(1, standingOrderId)
            ps.setObject(2, accountId)
            ps.setObject(3, payeeId)
            ps.setString(4, payeeName)
            ps.setString(5, payeeSortCode)
            ps.setString(6, payeeAccountNumber)
            ps.setString(7, reference)
            ps.setLong(8, amountMinorUnits)
            ps.setString(9, Currency.GBP.name)
            ps.setString(10, frequency.name)
            ps.setString(11, InstrumentStatus.ACTIVE.name)
            ps.setDate(12, Date.valueOf(startDate))
            ps.setTimestamp(13, Timestamp.from(nextPaymentAt))
            ps.setTimestamp(14, Timestamp.from(createdAt))
        }
        recordAudit(
            createdBy, "SEED_STANDING_ORDER_CREATED", "StandingOrder", standingOrderId,
            mapOf("payee" to payeeName, "amountMinorUnits" to amountMinorUnits),
        )
        return standingOrderId
    }

    fun insertDirectDebit(
        accountId: UUID,
        creditorName: String,
        creditorSortCode: String,
        creditorAccountNumber: String,
        reference: String,
        amountMinorUnits: Long,
        frequency: Frequency,
        startDate: LocalDate,
        nextCollectionAt: Instant,
        createdAt: Instant,
        createdBy: UUID,
        idGenerator: UuidGenerator? = null,
    ): UUID {
        val directDebitId = (idGenerator ?: uuidGenerator).generate()
        jdbcTemplate.update(
            "INSERT INTO direct_debits " +
                "(direct_debit_id, account_id, creditor_name, creditor_sort_code, creditor_account_number, " +
                "reference, amount_minor_units, currency, frequency, status, start_date, next_collection_at, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ) { ps ->
            ps.setObject(1, directDebitId)
            ps.setObject(2, accountId)
            ps.setString(3, creditorName)
            ps.setString(4, creditorSortCode)
            ps.setString(5, creditorAccountNumber)
            ps.setString(6, reference)
            ps.setLong(7, amountMinorUnits)
            ps.setString(8, Currency.GBP.name)
            ps.setString(9, frequency.name)
            ps.setString(10, InstrumentStatus.ACTIVE.name)
            ps.setDate(11, Date.valueOf(startDate))
            ps.setTimestamp(12, Timestamp.from(nextCollectionAt))
            ps.setTimestamp(13, Timestamp.from(createdAt))
        }
        recordAudit(
            createdBy, "SEED_DIRECT_DEBIT_CREATED", "DirectDebit", directDebitId,
            mapOf("creditor" to creditorName, "amountMinorUnits" to amountMinorUnits),
        )
        return directDebitId
    }

    private fun recordAudit(
        actorId: UUID,
        action: String,
        resourceType: String,
        resourceId: UUID,
        afterState: Map<String, Any?>,
    ) {
        auditWriter.record(
            AuditEvent(
                actorId = actorId,
                actorType = ActorType.SYSTEM,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId.toString(),
                afterState = afterState,
            )
        )
    }
}
